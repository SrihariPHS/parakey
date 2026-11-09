# Third-party notices

Parakey is built with the following open components. The app repository does not
redistribute model weights; they are downloaded by the user with a checksum
check.

## NVIDIA Parakeet TDT-CTC 110M

- Source: https://huggingface.co/nvidia/parakeet-tdt_ctc-110m
- Licence: Creative Commons Attribution 4.0 International (CC-BY-4.0)
- Use here: English speech-to-text, CTC branch, int8 ONNX.
- The ONNX export is provided by
  [VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8](https://huggingface.co/VocaHQ/sherpa-onnx-nemo-parakeet-tdt-ctc-110m-en-int8),
  derived from the FP32 export by
  [csukuangfj/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000](https://huggingface.co/csukuangfj/sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000).
- Changes: dynamic int8 weight quantisation. See the model card for details.

## sherpa-onnx

- Source: https://github.com/k2-fsa/sherpa-onnx
- Licence: Apache-2.0
- Component: `sherpa-onnx-2.0.1.aar`, including `libsherpa-onnx-jni.so`, the
  C/C++ APIs and the Kotlin `OfflineRecognizer` wrapper. The unmodified AAR is
  kept in `app/libs/`.

## ONNX Runtime

- Source: https://github.com/microsoft/onnxruntime
- Licence: MIT
- Component: `libonnxruntime.so` bundled inside the sherpa-onnx AAR.

```
MIT License

Copyright (c) Microsoft Corporation

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Android SDK

- Android platform APIs and build tools are used under the Android SDK licence.
