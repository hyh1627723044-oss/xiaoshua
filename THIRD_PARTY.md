# Third-party components

- sherpa-onnx 1.13.8: Copyright Xiaomi Corporation and contributors, Apache-2.0.
  Official AAR downloaded without modification; license bundled as `SHERPA_LICENSE.txt`.
- sherpa-onnx-kws-zipformer-wenetspeech-3.3M-2024-01-01-mobile:
  Official KWS release asset. Its model card declares Apache License 2.0;
  card bundled as `kws/MODEL_README.md`. Encoder and joiner use the provided int8 models.
- Silero VAD (`app/src/main/assets/vad/silero_vad.onnx`): Silero Team, MIT License.
  https://github.com/snakers4/silero-vad . Committed unmodified from the official sherpa-onnx
  `asr-models` release; SHA-256 pinned in `assets.lock.json`. Used only in cloud ASR mode.
- OkHttp 4.12.0 and Okio: Square, Inc., Apache-2.0. Used only for the optional cloud ASR / JEV requests.
- Gradle wrapper: Gradle contributors, Apache-2.0. Wrapper files originate from the
  official sherpa-onnx Android example; distribution pinned to 8.11.1 with its official SHA-256.
- ONNX Runtime is distributed within the official sherpa-onnx AAR.
  https://github.com/microsoft/onnxruntime (MIT).

ShortVideoAssistant by bunny-chz informed the interaction requirements. Its application
source and Baidu binaries are not incorporated into this independently implemented app.
