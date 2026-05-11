# 📊 [Order Service] AS-IS 아키텍처 성능 병목 리포트 (1차 부하테스트)

본 문서는 현재 주문 시스템(AS-IS) 아키텍처가 대규모 트래픽을 맞이했을 때 발생하는 **쓰기(Write) 및 읽기(Read) 병목 현상**을 수치와 로그로 증명한 1차 성능 테스트 리포트입니다.

---

## 1. 주문 쓰기(Write) 성능 한계 증명

> **테스트 목적:** 트랜잭션 내부에서 동기식 통신(Feign) 호출 및 타 서비스(Inventory)의 DB 낙관적 락 경합 발생 시, Order 서비스의 DB 커넥션 풀이 고갈되는 연쇄 장애 증명

### 🧪 Test Scope / Conditions

- **환경 어댑터:** `PerfReservationValidationAdapter` 사용 (외부 Reservation 서비스 우회)
- **실제 호출 타겟:** `Catalog Service`(조회), `Inventory Service`(재고 선점 낙관적 락 경합)
- **HikariCP 설정:** `maximum-pool-size: 10`, `minimum-idle: 2`, `connection-timeout: 5000ms` (빠른 병목 관찰을 위해 5초 Fail-Fast
  설정)
- **트랜잭션 동작:** `@Transactional` 블록 내에서 외부 HTTP 통신이 발생하여 커넥션을 장기 점유하는 아키텍처

### 📌 JMeter 타격 결과 (Summary Report)

- **요청 API:** `POST /api/v1/orders`
- **부하 규모:** 300 Threads 점진 증가 (총 3,000건 요청, Ramp-up 5초 스파이크 테스트)
- **결과 요약:**
    - **평균 지연 시간(Average):** `7,170ms` (약 7.1초)
    - **최대 지연 시간(Max):** `9,187ms` (약 9.1초)
    - **에러율(Error %):** `39.7%` (500 Internal Server Error 발생)
- **분석:** Inventory 서비스의 락 경합으로 인해 트랜잭션 처리가 지연되면서, 약 40%의 요청이 커넥션을 제때 얻지 못하고 버려지는 심각한 병목을 확인했습니다.

<img width="854" height="258" alt="create_order_as-is_jmeter-01-01" src="https://github.com/user-attachments/assets/00a5167c-38a4-434e-a710-d64c46a8dfb2" />

### 📌 병목 원인 증명: HikariCP 고갈 (Fail-Fast) 및 에러 로그

- 트랜잭션을 시작해 Order DB 커넥션을 점유한 상태에서, 외부 서비스의 응답 지연으로 인해 커넥션을 반환하지 못하는 **커넥션 풀 고갈** 현상이 발생했습니다.
- HikariCP 최대 개수(10개)가 소진된 후, 대기 중이던 요청들이 설정된 **5초(5001ms) 타임아웃**을 초과하여 `CannotCreateTransactionException`을 발생시키는 것을 로그로
  확인했습니다.

<img width="1253" height="705" alt="create_order_as-is_intellij-01-01" src="https://github.com/user-attachments/assets/c84cf955-4365-43f2-b7cb-1ed1f44b2d36" />

---

## 2. 주문 내역 읽기(Read) 성능 한계 증명

> **테스트 목적:** 인덱스가 없는 상태에서 특정 유저의 주문 내역을 조회할 때 발생하는 Full Table Scan(Seq Scan) 및 In-Memory Sort 병목 증명

### 📌 EXPLAIN ANALYZE 실행 계획 결과

- **쿼리:** `SELECT * FROM order_service.p_orders WHERE user_id = ? ORDER BY created_at DESC;`
- **실행 계획 분석:**
    1. **`Seq Scan on p_orders` 발생:** `user_id` 인덱스 부재로 인해 테이블 전체를 스캔했습니다. 이 과정에서 **9,606건**의 데이터를 일일이 필터링하는 비효율이
       발생했습니다.
    2. **`Sort Method: quicksort` 발생:** `created_at` 정렬 인덱스 부재로 DB가 데이터를 메모리에 올려 직접 퀵 소트를 수행했습니다.
- **결론:** 데이터가 누적될수록 조회가 기하급수적으로 느려지며, 메모리 부족(Disk Spill) 시 시스템 전체 성능 하락을 유발하는 구조적 결함입니다.

<img width="925" height="436" alt="read_order_as-is_intellij-01-01" src="https://github.com/user-attachments/assets/e1f52be8-44f7-4ffa-b33b-ffc52d3611c8" />

---

## 💡 종합 결론 및 향후 작업방향 (TO-BE 기획)

1. **쓰기 병목 해결:** Inventory 서비스에 **Redisson 분산 락**을 도입하여 락 경합 시간을 최소화
2. **동기 통신 제거:** **Outbox Pattern & Kafka** 기반 비동기 이벤트 통신을 도입하여 HikariCP 커넥션 점유 문제를 근본적으로 해결
3. **읽기 성능 개선:** `user_id` 및 `created_at` 컬럼에 **복합 인덱스**를 적용하여 Seq Scan 제거 및 Index Scan 유도
