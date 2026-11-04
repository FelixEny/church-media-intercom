# Third-party notices

This project distributes a prebuilt WebRTC binary and links several open-source
libraries. Their licences are reproduced or referenced below. Full licence texts
ship with each upstream artifact.

## WebRTC (libwebrtc)

- Component: `io.github.webrtc-sdk:android` (prebuilt libwebrtc for Android)
- Upstream: https://webrtc.googlesource.com/src and https://github.com/webrtc-sdk
- Licence: **BSD 3-Clause**

```
Copyright (c) 2011, The WebRTC project authors. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.
  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.
  * Neither the name of Google nor the names of its contributors may be used to
    endorse or promote products derived from this software without specific
    prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The libwebrtc build also contains Opus, which is BSD-3 licensed
(https://opus-codec.org/license/), and other codecs and libraries documented in
its `LICENSE` file.

## AndroidX, Jetpack Compose, Material Components

- Licence: **Apache License 2.0** — https://www.apache.org/licenses/LICENSE-2.0
- Includes `androidx.core`, `androidx.lifecycle`, `androidx.activity`,
  `androidx.datastore`, and Compose UI/Material3 (including Material Icons).

## Kotlin, kotlinx.coroutines, kotlinx.serialization

- Licence: **Apache License 2.0** — https://www.apache.org/licenses/LICENSE-2.0

## Ktor

- Licence: **Apache License 2.0** — https://www.apache.org/licenses/LICENSE-2.0

## OkHttp

- Licence: **Apache License 2.0** — https://www.apache.org/licenses/LICENSE-2.0

## ZXing (if/when added for QR codes)

- Licence: **Apache License 2.0** — https://www.apache.org/licenses/LICENSE-2.0

## Android platform APIs

`android.media.*`, `android.net.nsd.*`, and `android.bluetooth.*` are used under
the Android SDK terms. The Android robot artwork is not used in this project.
