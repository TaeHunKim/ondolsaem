# OndolSaem / 온돌샘 (on-device model host, Android)
다른 앱(첫 클라이언트: 눈소리 `taehunkim/nunsori`)에 온디바이스 AI 모델을 바인드 AIDL 서비스로 제공하는 앱. applicationId·패키지 `io.github.taehunkim.ondolsaem`, Kotlin + Compose, minSdk 29 / target 36 / compile 37.

## 모듈
- `:sdk`: 클라이언트 계약(AIDL, parcelable, `OndolSaem` 상수)과 클라이언트 라이브러리. JitPack으로 배포(`com.github.TaeHunKim:ondolsaem:<태그>`, 태그는 `sdk-vX.Y.Z`). 버전은 `sdk/build.gradle.kts`의 `version`.
- `:app`: 호스트 앱. 버전은 `app/build.gradle.kts`의 `appVersionName` 한 곳.

## 계약 규칙 (sdk)
- AIDL 메서드는 **끝에만 추가**한다(고정 트랜잭션 id). 순서 변경·삭제·시그니처 변경 금지. parcelable 필드도 끝에만 추가.
- 계약을 바꾸면 `OndolSaem.Api`에 새 레벨(`V2`…)을 추가하고 `API_VERSION`을 올리며, 새 메서드 주석에 레벨을 적고 SDK 호출부에서 `call(minApi = Api.Vx)`로 막는다. 연결 시 `hello(ClientInfo) → HostInfo`로 버전을 주고받는다. `HostContainer.MIN_CLIENT_API`는 옛 계약을 더 이상 지킬 수 없을 때만 올린다. 자세히: `docs/architecture.md` "Versioning".
- 새 모델 카테고리는 새 AIDL 인터페이스로 추가한다(기존 인터페이스 수정 금지).
- 큰 데이터(이미지, 오디오)는 Binder 1MB 한도 때문에 `ParcelFileDescriptor`로 보낸다.
- sdk를 바꾸면 클라이언트(눈소리) 빌드도 확인한다: 눈소리 `local.properties`에 `ondolsaem.dir=../ondolsaem`.

## 문서 유지 규칙 (코드 구현 시 항상 적용)
코드와 관련 문서를 같은 커밋에서 갱신한다. 갱신이 필요 없다고 판단하면 이유를 한 줄 남긴다.

| 변경 | 갱신할 문서 |
|---|---|
| 새 기능·화면·설정 | `README.md` + `README.ko.md`(기능 표) |
| 서비스 계약·카테고리·상주·알림·접근 제어 | `docs/architecture.md` |
| 모델 추가·교체, 의존성, 라이선스 | `THIRD_PARTY_NOTICES.md`, (모델 문서 생기면) `docs/models.md` |
| 빌드·스크립트·테스트·배포 방법 | `docs/development.md` |
| 클라이언트 API 사용법 | `docs/client-guide.md` |
| 권한·네트워크·저장 데이터 | `docs/privacy.md` |

- 문자열은 `values/strings.xml`(en)과 `values-ko/strings.xml`(ko)의 키가 같아야 한다(`StringsParityTest`).
- 모델 파일(*.litertlm, *.onnx 등)과 `app/libs/*.aar`은 git 제외.

## 환경·워크플로우
- `source scripts/env.sh`(JAVA_HOME/ANDROID_HOME/PATH, APP_ID). AVD `lsa_api36`(눈소리와 공용).
- `scripts/emu-start.sh [--window]` / `scripts/emu-stop.sh`, `scripts/run.sh`(installDebug + 실행), `scripts/logcat.sh [N]`, `scripts/screenshot.sh`.
- `./gradlew assembleDebug testDebugUnitTest` / `./gradlew connectedDebugAndroidTest`.
- 자세히: `docs/development.md`, 설계: `docs/architecture.md`.
