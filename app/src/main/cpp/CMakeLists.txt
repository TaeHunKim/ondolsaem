# Small JNI bridge to the ONNX Runtime that ships inside the sherpa-onnx AAR (libonnxruntime.so, 1.28.x).
# Two copies of ONNX Runtime cannot live in one APK (their symbols are versioned per release), so models that
# sherpa-onnx does not cover (SFace face embeddings, the NLI decision model) run through this bridge on sherpa's copy. Nothing is linked
# at build time: the library is opened with dlopen and the C API is reached through OrtGetApiBase.
cmake_minimum_required(VERSION 3.22)
project(ortbridge CXX)
add_library(ortbridge SHARED ort_bridge.cpp)
target_include_directories(ortbridge PRIVATE include/onnxruntime)
target_compile_features(ortbridge PRIVATE cxx_std_17)
target_link_libraries(ortbridge PRIVATE log dl)
