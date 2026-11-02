# OndolSaem / 온돌샘

[English](README.md) | **한국어**

온돌샘은 다른 앱을 위해 온디바이스 AI 모델을 맡아 주는 Android 앱입니다. 카테고리(텍스트·이미지·오디오 임베딩, 채팅 LLM, OCR, 음성 인식)별로 큐레이션한 모델을 받아 두고, 카테고리마다 정한 정책대로 메모리에 올려 두며, 바인드 AIDL 서비스로 다른 앱에 제공합니다. 모델은 앱마다가 아니라 모든 앱을 통틀어 한 번만 올라갑니다.

이름은 집 전체를 밑에서 데우는 *온돌*과, 누구나 와서 물을 길어 가는 *샘*을 합친 것입니다. 역두문자로는:
- **ONDOL**: On-device Neural Deployment & Orchestration Layer
- **SAEM**: Shared AI Engine Manager

상태: 초기 개발 단계(0.1.0 뼈대). 첫 클라이언트는 온디바이스 시맨틱 검색 앱 [눈소리 / Nunsori](https://github.com/taehunkim/nunsori)입니다.

## 계획된 기능

| 기능 | 설명 |
|---|---|
| 모델 카테고리 | 카테고리마다 간단한 설명과 큐레이션 모델 목록. 받은 모델 중 하나를 카테고리 기본값으로 지정 |
| 다운로드 | 미터링 네트워크(모바일 데이터, 미터링 Wi-Fi)에서 받기 전에 경고. "Wi-Fi에서 받기" 선택 가능 |
| 모델 변경 | 임베딩 모델을 바꾸면 다시 임베딩해야 하는 앱을 알려 주는 경고. 사용하는 앱에 즉시 또는 다음 연결 때 알림 |
| 상주 | 카테고리별로 상시 상주, 사용 직후 내림, N분 후 내림. 내려간 모델은 다음 요청 때 자동으로 다시 올림 |
| 대시보드 | 기기 메모리, 온돌샘의 메모리 사용량, 올라간 모델과 그 모델을 쓰는 앱 |
| 클라이언트 SDK | `com.github.taehunkim.ondolsaem:sdk`, [docs/architecture.md](docs/architecture.md) 참고 |

## 문서

- [docs/architecture.md](docs/architecture.md): 서비스 계약, 카테고리, 상주, 모델 변경 알림, 접근 제어
- [docs/development.md](docs/development.md): 빌드, 스크립트, SDK 배포, 클라이언트 앱과 함께 작업하기

## 라이선스

Apache License 2.0, [LICENSE](LICENSE) 참고. 서드파티 구성요소: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
