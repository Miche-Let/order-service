# 📊 [Order Service] AS-IS 아키텍처 성능 병목 리포트 (1차 부하테스트)

본 문서는 현재 주문 시스템(AS-IS) 아키텍처가 대규모 트래픽을 맞이했을 때 발생하는 **쓰기(Write) 및 읽기(Read) 병목 현상**을 수치와 로그로 증명한 1차 성능 테스트 리포트입니다.

---

## 1. 주문 쓰기(Write) 성능 한계 증명

> **테스트 목적:** 동기식 통신(Feign)과 DB 낙관적 락(@Version) 기반의 아키텍처에서 300 VUser 동시 타격 시 발생하는 커넥션 풀 고갈 및 지연 증명

### 📌 JMeter 타격 결과 (Summary Report)

- **요청 타겟:** `POST /api/v1/orders`
- **부하 규모:** 300 Threads
- **결과 요약:**
    - **평균 지연 시간(Average):** `11,291ms` (약 11초)
    - **최대 지연 시간(Max):** `24,274ms` (약 24초)
- **분석:** Inventory 서비스의 락 경합으로 인해 처리 시간이 비정상적으로 지연되며 사용자 경험(UX)이 심각하게 훼손됨을 확인했습니다.

<img width="845" height="231" alt="create_order_as-is_jmeter" src="https://github.com/user-attachments/assets/51312819-9b35-4992-b8e9-7368f5c590e1" /> 

### 📌 병목 원인 증명: HikariCP 고갈 및 에러 로그

- 트랜잭션(`@Transactional`) 진입 후 타 서비스(Inventory)와 동기식 통신을 진행함에 따라, 응답 대기 시간 동안 Order 서비스의 DB 커넥션을 반환하지 못하는 Deadlock 상태가
  발생했습니다.
- 그 결과, DB 헬스 체크조차 **15,270ms**가 소요되는 커넥션 풀(HikariPool) 고갈 장애와 `429 Too Many Requests` / `500 Server Error`가 확인되었습니다.

<img width="1247" height="391" alt="create_order_as-is_intellij_1" src="https://github.com/user-attachments/assets/6dec2836-5d66-4da3-a07e-29f48a85b3c9" />

<img width="1256" height="62" alt="create_order_as-is_intellij_2" src="https://github.com/user-attachments/assets/1d1923ca-564f-4906-93b9-77c19a2678ec" />

---

## 2. 주문 내역 읽기(Read) 성능 한계 증명

> **테스트 목적:** 인덱스가 없는 상태에서 특정 유저의 주문 내역을 조회할 때 발생하는 Full Table Scan(Seq Scan) 슬로우 쿼리 증명

### 📌 EXPLAIN ANALYZE 실행 계획 결과

- **쿼리:** `SELECT * FROM p_orders WHERE user_id = ? ORDER BY created_at DESC;`
- **결과:** **`Seq Scan on p_orders`** 발생
- **분석:** `user_id`를 조건으로 검색하지만 인덱스가 존재하지 않아 테이블 전체를 스캔하고 있습니다. (`Rows Removed by Filter: 2995`). 데이터가 수십만 건 이상 적재될 경우
  심각한 조회 지연(Slow Query)을 유발할 명백한 병목 포인트입니다.

<img width="934" height="510" alt="read_order_as-is_intellij" src="https://github.com/user-attachments/assets/e628dd8a-30ac-41c0-afe8-ea5c2d933b08" />

---

## 💡 종합 결론 및 향후 작업방향 (TO-BE 기획)

1. **쓰기 병목 해결:** Inventory 서비스에 **Redisson 분산 락**을 도입하여 락 경합 시간을 최소화합니다.
2. **동기 통신 제거:** 비동기 이벤트 기반 통신(**Outbox Pattern & Kafka**)을 도입하여 트랜잭션 범위 내의 외부 통신을 제거하고 HikariCP 커넥션 점유 문제를 해결합니다.
3. **읽기 성능 개선:** Order 조회 조건에 **복합 인덱스**를 적용하여 Seq Scan을 Index Scan으로 개선합니다.
