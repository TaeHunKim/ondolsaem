"""Builds the graph template OndolSaem uses to turn the fp32 decision model into weight-only int8 on the phone.

Why: the published int8 builds of mDeBERTa (dynamic quantization: activations to uint8) give wrong answers on the ONNX
Runtime bundled with sherpa-onnx (1.28.2 for Android; on desktop only builds >= 1.24 with graph fusions get them
right). Quantizing only the weights (int8 per output column, activations stay float) is right everywhere.

Output (app/src/main/assets/nli-w8/):
  model.onnx   the converted graph; every initializer is external data in "weights.bin" (no weights inside)
  recipe.tsv   one line per initializer: name, op, source initializer, offset, length (bytes in weights.bin)
               ops: copy (fp32 as is), qcol / scol (MatMul weight [K,N] -> int8 per column / its fp32 scales),
                    qrow / srow (embedding table [V,D] -> int8 per row / its fp32 scales [V,1])
The phone reads the fp32 file's initializers, applies the ops and writes weights.bin next to model.onnx
(engine/NliW8Converter.kt). Rerun this script when the source model changes (its SHA-256 is pinned in the catalog).

Usage: uv run --with onnx --with numpy python scripts/make-nli-w8-template.py <fp32 model.onnx> [--check]
  --check also converts on the desktop like the phone does and writes /tmp-free check files into the scratch folder
  given by NLI_CHECK_DIR, so the result can be compared with onnxruntime.
"""
import os, sys
import numpy as np
import onnx
from onnx import helper, numpy_helper as nh

src = onnx.load(sys.argv[1])
g = src.graph
inits = {i.name: i for i in g.initializer}
shape = {i.name: list(i.dims) for i in g.initializer}
recipe = []  # (name, op, source, nbytes, dtype, dims)
nodes, consumed = [], set()

for n in g.node:
    if n.op_type == "MatMul" and n.input[1] in inits and len(shape[n.input[1]]) == 2:
        b = n.input[1]
        k, m = shape[b]
        recipe += [(b + "_q", "qcol", b, k * m, onnx.TensorProto.INT8, [k, m]),
                   (b + "_s", "scol", b, m * 4, onnx.TensorProto.FLOAT, [m])]
        nodes += [helper.make_node("Cast", [b + "_q"], [b + "_qf"], to=onnx.TensorProto.FLOAT, name=b + "_DQc"),
                  helper.make_node("Mul", [b + "_qf", b + "_s"], [b + "_dq"], name=b + "_DQm")]
        n.input[1] = b + "_dq"
        consumed.add(b)
    elif n.op_type == "Gather" and n.input[0] in inits and shape[n.input[0]][0] > 100_000:
        b = n.input[0]
        v, d = shape[b]
        recipe += [(b + "_q", "qrow", b, v * d, onnx.TensorProto.INT8, [v, d]),
                   (b + "_s", "srow", b, v * 4, onnx.TensorProto.FLOAT, [v, 1])]
        out, ids = n.output[0], n.input[1]
        nodes += [helper.make_node("Gather", [b + "_q", ids], [out + "_gq"], name=b + "_Gq"),
                  helper.make_node("Cast", [out + "_gq"], [out + "_gf"], to=onnx.TensorProto.FLOAT, name=b + "_C"),
                  helper.make_node("Gather", [b + "_s", ids], [out + "_gs"], name=b + "_Gs"),
                  helper.make_node("Mul", [out + "_gf", out + "_gs"], [out], name=b + "_M")]
        consumed.add(b)
        continue
    nodes.append(n)

used = {x for n in nodes for x in n.input}
for i in g.initializer:  # everything else is copied (also a consumed one still used elsewhere)
    if i.name in used:
        assert i.data_type == onnx.TensorProto.FLOAT, (i.name, i.data_type)
        count = int(np.prod(i.dims)) if len(i.dims) else 1
        recipe.append((i.name, "copy", i.name, count * 4, onnx.TensorProto.FLOAT, list(i.dims)))

out_inits, lines, offset = [], [], 0
for name, op, source, nbytes, dtype, dims in recipe:
    t = onnx.TensorProto(name=name, data_type=dtype, dims=dims)
    t.data_location = onnx.TensorProto.EXTERNAL
    for k, v in (("location", "weights.bin"), ("offset", str(offset)), ("length", str(nbytes))):
        e = t.external_data.add(); e.key = k; e.value = v
    out_inits.append(t)
    lines.append(f"{name}\t{op}\t{source}\t{offset}\t{nbytes}")
    offset += nbytes

del g.node[:]; g.node.extend(nodes)
del g.initializer[:]; g.initializer.extend(out_inits)
dest = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "nli-w8")
os.makedirs(dest, exist_ok=True)
with open(os.path.join(dest, "model.onnx"), "wb") as f:
    f.write(src.SerializeToString())
with open(os.path.join(dest, "recipe.tsv"), "w") as f:
    f.write("\n".join(lines) + "\n")
print(f"{len(lines)} initializers, weights.bin {offset} bytes, graph {os.path.getsize(os.path.join(dest, 'model.onnx'))} bytes")

if "--check" in sys.argv:  # the phone's conversion, in numpy
    out = os.environ["NLI_CHECK_DIR"]
    fp = {i.name: nh.to_array(i).astype(np.float32) for i in onnx.load(sys.argv[1]).graph.initializer}
    with open(os.path.join(out, "weights.bin"), "wb") as f:
        for name, op, source, nbytes, _, _ in recipe:
            w = fp[source]
            if op == "copy": b = w.tobytes()
            elif op in ("qcol", "scol"):
                s = np.abs(w).max(axis=0) / 127.0; s[s == 0] = 1e-8
                b = (np.clip(np.round(w / s), -127, 127).astype(np.int8) if op == "qcol" else s.astype(np.float32)).tobytes()
            else:
                s = np.abs(w).max(axis=1) / 127.0; s[s == 0] = 1e-8
                b = (np.clip(np.round(w / s[:, None]), -127, 127).astype(np.int8) if op == "qrow" else s.astype(np.float32)).tobytes()
            assert len(b) == nbytes, name
            f.write(b)
    import shutil; shutil.copy(os.path.join(dest, "model.onnx"), os.path.join(out, "model.onnx"))
    print("check files in", out)
