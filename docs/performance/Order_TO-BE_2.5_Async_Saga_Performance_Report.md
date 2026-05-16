# 📊 [Order Service] 비동기 Saga 아키텍처 성능 검증 및 병목 분석 리포트 (2.5차 부하테스트)

본 문서는 주문 생성 로직을 동기식 분산 락 모델에서 **Kafka 기반의 비동기 메시징 아키텍처(Choreography Saga)로** 전환한 후의 성능 변화와, MSA 환경의 전형적인 '풍선 효과(Balloon
Effect)'를 분석하여 최종 아키텍처 개선 방향을 도출한 리포트입니다.

---

## 1. 비동기 전환 후 성능 측정 및 '풍선 효과' 발견

> **테스트 목적:** 인벤토리 서비스(Write)와의 통신을 비동기로 분리한 후 목표 처리량(200 TPS) 달성 여부 확인

- **부하 규모:** 100 Threads 동시 요청 (총 1,000건 스파이크)
- **1차 측정 결과 (카탈로그 동기 조회 유지):**
    - **처리량(Throughput):** `31.5 / sec` (기대치 미달)
    - **평균 지연 시간(Average):** `2,990 ms` (약 3초)
    - **에러율(Error %):** `0.0%`
- **병목 원인 분석:** 인벤토리 락 대기로 인한 쓰기 병목은 에러율 0%로 완벽하게 해소되었습니다. 그러나 주문 생성 직전 호출되는 **카탈로그 상품 정보 조회(동기 Feign) 로직이 새로운 병목
  지점**으로 작용함을 확인했습니다. 1,000건의 동시 읽기(Read) 요청이 Disk 기반의 MongoDB에 쏠리면서 디스크 I/O 한계에 부딪힌 전형적인 '풍선 효과'입니다.

<img width="856" height="239" alt="스크린샷 2026-05-16 오전 2 14 09" src="https://github.com/user-attachments/assets/bd4a9952-2d25-44d2-8316-4ff4b55751e3" />

---

## 2. 병목 지점 우회(Bypass)를 통한 비동기 인프라 순수 성능 증명

> **테스트 목적:** 카탈로그 조회 병목이 해결된다면, 현재 구축된 Kafka 비동기 큐잉 시스템이 목표 처리량(200 TPS)을 달성할 수 있는지 순수 쓰기(Write) 성능을 검증

- **테스트 환경:** 카탈로그 Feign 통신 로직을 임시 우회(Mocking)하여, Read 지연 시간이 0ms에 수렴하는 **Redis Cache Hit 상황을 시뮬레이션**했습니다.
    ```java
    for (CreateOrderCommand.OrderItemCommand item : command.items()) {
    //    var catalogRes = catalogClient.validateOption(item.optionId());
    //    if (catalogRes == null || catalogRes.data() == null) {
    //        throw new IllegalArgumentException("상품 옵션 정보를 확인할 수 없습니다.");
    //    }
    //
    //    var catalogData = catalogRes.data();
    
        //FIXME 테스트 후에 윗줄 코드 다시 살리고 아래 덩어리 삭제하기
        // 통신을 기다리지 않고, 0초 만에 더미 데이터를 반환하도록 강제 우회함
        var catalogData = new CatalogClient.OptionValidationResponse(
            item.optionId(),
            "병목 증명용 더미 상품",
            new java.math.BigDecimal("10000")
        );
    
        // 카탈로그에서 검증된 진짜 이름과 가격으로 OrderItem 스냅샷 생성
        orderItems.add(OrderItem.create(
            catalogData.optionId(),
            catalogData.name(),
            catalogData.totalPrice(),
            item.quantity()
        ));
    }
    ```
- **2차 측정 결과 (병목 우회 시):**
    - **처리량(Throughput):** `373.9 / sec` **(📈 약 1,180% 성능 향상)**
    - **평균 지연 시간(Average):** `197 ms` **(📉 약 93% 지연 시간 단축)**
    - **에러율(Error %):** `0.0%`

<img width="847" height="280" alt="스크린샷 2026-05-16 오전 2 21 35" src="https://github.com/user-attachments/assets/1d5a858f-e748-41f2-b23a-c7b6613a637f" />

---

### 📌 성능 비교 요약 (JMeter Summary)

| 지표             | 동기식 카탈로그 조회 (Read 병목 발생) | Read 병목 해소 시 (비동기 순수 성능) | 변화율        |
|:---------------|:-------------------------|:-------------------------|:-----------|
| **Throughput** | 31.5 TPS                 | **373.9 TPS**            | `+ 1,187%` |
| **Average**    | 2,990 ms                 | **197 ms**               | `- 93.4%`  |
| **Max Time**   | 7,381 ms                 | **858 ms**               | `- 88.3%`  |
| **Error Rate** | 0.0%                     | **0.0%**                 | `유지`       |

---

## 💡 종합 결론 및 향후 작업방향 (3차 기획)

1. **비동기 Saga 아키텍처의 성공적 도입:** Inventory 서비스와의 통신을 Kafka 비동기로 전환함으로써, 무거운 트랜잭션과 락 경합 로직을 백그라운드로 분리했습니다. 이를 통해 **에러율
   0**의 안정성과 함께, 쓰기 관점에서 **373 TPS**를 소화해 내는 고성능 확장성을 확보했습니다.
2. **읽기 성능 최적화의 당위성 증명:** 본 테스트를 통해 '카탈로그 조회 성능'이 전체 시스템 처리량의 최종 발목을 잡고 있음이 수치로 증명되었습니다.
3. **[향후 작업방향] 카탈로그 Redis 캐시 도입:** 시뮬레이션 결과(373 TPS)를 실제 운영 환경에서 달성하기 위해, 카탈로그 메인 목록 조회 API에 **Redis Cache-Aside 패턴**을
   도입합니다. Disk I/O를 Memory I/O로 전환하여 읽기 지연을 10ms 이하로 단축시킬 예정입니다.
