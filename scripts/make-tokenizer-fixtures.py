"""Writes the expected token ids the Kotlin UnigramTokenizer must reproduce (UnigramTokenizerTest).

Usage: uv run --with tokenizers python scripts/make-tokenizer-fixtures.py <tokenizer.json>
The tokenizer.json is the one of the decision model (onnx-community/mDeBERTa-v3-base-xnli-multilingual-nli-2mil7-ONNX);
it is not committed, the test reads it from NLI_TOKENIZER and skips without it.
"""
import sys
from tokenizers import Tokenizer

TEXTS = [
    "안녕하세요 Hello 世界",
    "오늘 점심값 12,000원 카드 영수증. 김밥천국 강남점.",
    "이 글은 여행(관광, 휴가)에 관한 내용이다.",
    "Invoice #4411: total due $320, payment by Nov 30.",
    "  여러   칸의\t탭과\n줄바꿈  ",
    "ｆｕｌｌｗｉｄｔｈ ＡＢＣ ①②③ ㈜ ㎏ ﬁ",
    "emoji 😀👍🏽 and rare 𐞁 ꟲ chars",
    "물결～표시 ㊿ ㋿ 끝",
    "zero​width line﻿bom",
    "Ünïcödé àccents café naïve",
    "日本語のテキストと中文文本",
    "Москва и Санкт-Петербург",
    "x" * 40 + " supercalifragilisticexpialidocious",
    "",
]
PAIRS = [("다음 주 제주도 3박 4일 일정: 첫날 성산일출봉. " * 20, "이 글은 여행에 관한 내용이다.", 64)]

tok = Tokenizer.from_file(sys.argv[1])
# one case per line, tab-separated: kind, hex UTF-8 texts, max tokens, comma-separated ids (no JSON parser in unit tests)
hx = lambda t: t.encode("utf-8").hex()
lines = []
for t in TEXTS:
    lines.append("\t".join(["single", hx(t), "", "0", ",".join(map(str, tok.encode(t, add_special_tokens=False).ids))]))
for a, b, n in PAIRS:
    tok.enable_truncation(n, strategy="only_first")
    lines.append("\t".join(["pair", hx(a), hx(b), str(n), ",".join(map(str, tok.encode(a, b).ids))]))
    tok.no_truncation()
open("app/src/test/resources/tokenizer-fixtures.tsv", "w").write("\n".join(lines) + "\n")
print("wrote", len(lines), "cases")
