# 📊 [Order Service] AS-IS 아키텍처 성능 병목 리포트 (1차 부하테스트)

본 문서는 현재 주문 시스템(AS-IS) 아키텍처가 대규모 트래픽을 맞이했을 때 발생하는 **쓰기(Write) 및 읽기(Read) 병목 현상**을 수치와 로그로 증명한 1차 성능 테스트 리포트입니다.

---

## 1. 주문 쓰기(Write) 성능 한계 증명

> **테스트 목적:** 트랜잭션 내부에서 동기식 통신(Feign) 호출 및 타 서비스(Inventory)의 DB 낙관적 락 경합 발생 시, 서버에 발생하는 성능 지연 및 동시성 처리 실패 한계 증명

### 🧪 Test Scope / Conditions

- **환경 어댑터:** `PerfReservationValidationAdapter` 사용 (외부 Reservation 서비스 우회)
- **실제 호출 타겟:** `Inventory Service` (재고 선점 낙관적 락 경합 - 최대 3회 재시도 설정)
- **HikariCP 설정:** `maximum-pool-size: 10`, `minimum-idle: 2`, `connection-timeout: 5000ms`
- **트랜잭션 동작:** `@Transactional` 블록 내에서 외부 HTTP 통신이 발생하여 커넥션을 장기 점유하는 아키텍처

### 📌 JMeter 타격 결과 (Summary Report)

- **요청 API:** `POST /api/v1/orders`
- **부하 규모:** 100 Threads (총 1,000건 요청, 1 Ramp-up 스파이크 테스트)
- **결과 요약:**
    - **평균 지연 시간(Average):** `3,417ms` (약 3.4초)
    - **최대 지연 시간(Max):** `3,936ms` (약 3.9초)
    - **에러율(Error %):** `0.2%` (429 Too Many Requests 예외 발생)
    - **처리량(Throughput):** `27.6 /sec`
- **분석:** Inventory 서비스의 데이터베이스 낙관적 락(Optimistic Lock) 경합이 극심하게 발생하여, 처리량이 목표치(200 TPS)에 한참 못 미치는 27.6 TPS로 급감했습니다. 또한
  일부 요청은 최대 재시도(3회)를 모두 소진하고 처리에 실패(Error 0.2%)하는 한계를 보였습니다.

<img width="848" height="205" alt="스크린샷 2026-05-15 오후 6 20 28" src="https://github.com/user-attachments/assets/2f36d122-67b5-4f93-97a5-09615648580e" />

### 📌 병목 원인 증명: 동시성 경합 및 429 에러 로그

- 대규모 트래픽이 집중되자 인벤토리 서비스에서 `재고 차감 동시성(낙관적 락) 충돌 발생. 재시도 횟수: 3/3` 한계치 도달 로그가 다수 발생했습니다.
- 결국 낙관적 락 재시도를 소진한 요청들은 `[BusinessException] 현재 주문 요청이 많습니다.` 에러를 발생시켰고, 이를 호출한 오더 서비스는
  `Status=429 (Too Many Requests)` 통신 에러를 반환하며 주문 생성에 실패했습니다.

<img width="1246" height="367" alt="스크린샷 2026-05-15 오후 6 21 02" src="https://github.com/user-attachments/assets/37db39f4-c552-42c1-b92e-42ed178635e7" />  

<img width="1251" height="662" alt="스크린샷 2026-05-15 오후 6 23 07" src="https://github.com/user-attachments/assets/b1d773e0-b6ef-4c6c-ba99-844af7dfd553" />

---

## 2. 주문 내역 읽기(Read) 성능 한계 증명

> **테스트 목적:** 인덱스가 없는 상태에서 특정 유저의 주문 내역을 조회할 때 발생하는 Full Table Scan(Seq Scan) 및 In-Memory Sort 병목 증명

### 📌 EXPLAIN ANALYZE 실행 계획 결과

- **쿼리:** `SELECT * FROM order_service.p_orders WHERE user_id = ? ORDER BY created_at DESC;`
- **실행 계획 분석:**
    1. **`Seq Scan on p_orders` 발생:** `user_id` 인덱스 부재로 인해 테이블 전체를 스캔했습니다. 이 과정에서 조건에 일치하지 않는 더미 데이터 **998건**(Rows
       Removed
       by Filter: 998)을 불필요하게 스캔하고 필터링하는 비효율이 발생했습니다.
    2. **`Sort Method: quicksort` 발생:** `created_at` 정렬 인덱스 부재로 DB가 디스크의 데이터를 메모리(25kB)에 올려 직접 퀵 소트를 수행했습니다.
- **결론:** 현재 데이터가 적은 상황임에도 Seq Scan과 in-memory 정렬이 발생하고 있습니다. 데이터가 누적될수록 I/O 비용이 급증하며, 대량 조회 시 메모리 부족(Disk Spill)으로 인한
  시스템 전체 성능 하락을 유발하는 구조적 결함입니다.

<img width="920" height="427" alt="스크린샷 2026-05-15 오후 6 25 44" src="https://github.com/user-attachments/assets/c0cf9f03-066d-4d77-8472-b280c58710b1" />

---

## 💡 종합 결론 및 향후 작업방향 (TO-BE 기획)

1. **쓰기 병목 및 동시성 해결:** Inventory 서비스에 **Redisson 분산 락**을 도입하여 무의미한 낙관적 락 재시도 낭비를 없애고, 락 획득 대기를 통해 동시성 처리 성능을 극대화
2. **동기 통신 대기 제거:** **Outbox Pattern & Kafka** 기반 비동기 통신을 도입하여, 외부 서비스 응답 지연으로 인한 성능 하락(Avg 3.4초 지연)을 원천 차단
3. **읽기 성능 개선:** `user_id` 및 `created_at` 컬럼에 **복합 인덱스**를 적용하여 Seq Scan 및 in-memory 정렬 비용 제거 (Index Scan 유도)
