# 포인트 관리 시스템 - 동시성 제어 보고서

## 목차
1. [개요](#개요)
2. [동시성 문제 분석](#동시성-문제-분석)
3. [동시성 제어 방식](#동시성-제어-방식)
4. [구현 상세](#구현-상세)
5. [테스트 전략](#테스트-전략)
6. [성능 및 한계](#성능-및-한계)
7. [향후 개선 방향](#향후-개선-방향)

---

## 개요

본 시스템은 사용자의 포인트 충전/사용 기능을 제공하며, **동시성 환경에서 데이터 정합성을 보장**하기 위해 사용자별 Lock 기반 동시성 제어를 구현했습니다.

### 주요 기능
- 포인트 조회
- 포인트 충전
- 포인트 사용
- 포인트 히스토리 조회

---

## 동시성 문제 분석

### 발생 가능한 동시성 문제

#### 1. Race Condition (경쟁 상태)
```
Thread A: read(user1) → point = 1000
Thread B: read(user1) → point = 1000
Thread A: write(user1, 1000 + 100) → point = 1100
Thread B: write(user1, 1000 + 50) → point = 1050  ❌ Lost Update!
```
**문제점**: Thread A의 충전(+100)이 사라지고 최종 포인트가 1050이 됨

#### 2. Dirty Read (더티 리드)
```
Thread A: read(user1) → point = 1000
Thread A: write(user1, 1100) (커밋 전)
Thread B: read(user1) → point = 1100  ❌ Uncommitted Read!
Thread A: rollback → point = 1000
```
**문제점**: 커밋되지 않은 데이터를 읽어서 잘못된 판단을 할 수 있음

#### 3. Lost Update (갱신 손실)
```
초기 포인트: 1000
Thread A: +100 충전 시작
Thread B: +200 충전 시작
Thread A: 1000 + 100 = 1100 저장
Thread B: 1000 + 200 = 1200 저장  ❌ A의 작업 손실!
최종: 1200 (예상: 1300)
```

#### 4. Phantom Read (유령 읽기)
```
Thread A: count(histories) → 10개
Thread B: insert(new history)
Thread A: count(histories) → 11개  ❌ 같은 트랜잭션 내 다른 결과!
```

---

## 동시성 제어 방식

### 선택한 방식: **사용자별 ReentrantLock**

#### 왜 이 방식을 선택했는가?

| 방식 | 장점 | 단점 | 선택 여부 |
|------|------|------|-----------|
| **synchronized** | 간단한 구현 | 세밀한 제어 불가, 타임아웃 불가 | ❌ |
| **ReentrantLock** | 타임아웃, 공정성 제어 가능 | synchronized보다 복잡 | ✅ **채택** |
| **@Transactional(Isolation)** | Spring 통합 용이 | DB 종속적, 성능 오버헤드 | ❌ |
| **Optimistic Lock** | 충돌 적을 때 효율적 | 충돌 많으면 재시도 빈번 | ❌ |
| **Distributed Lock (Redis)** | 분산 환경 지원 | 외부 의존성, 복잡도 증가 | ❌ (단일 서버) |

### 핵심 설계 원칙

#### 1. 사용자별 격리 (User-Level Isolation)
```kotlin
// ✅ 사용자별 독립적인 Lock
User 1: [Lock 1] → 충전/사용 순차 처리
User 2: [Lock 2] → 충전/사용 순차 처리 (User 1과 독립적)
User 3: [Lock 3] → 충전/사용 순차 처리 (User 1, 2와 독립적)
```

**장점**:
- 서로 다른 사용자 간 요청은 병렬 처리 가능 (성능 향상)
- 동일 사용자 요청만 순차 처리하여 정합성 보장

#### 2. Critical Section (임계 영역) 최소화

**핵심 원칙**: 상태 조회/검증/변경은 반드시 Lock 안에서 원자적으로 수행

```kotlin
// ❌ 나쁜 예: 상태 의존적 연산이 Lock 밖에 있음 (TOCTOU 문제)
fun useUserPoint(userId: Long, amount: Long): UserPoint {
    val user = getUserPoint(userId)  // Lock 밖에서 조회
    user.validateSufficientPoints(amount)  // 검증 시점과 사용 시점 사이 gap!

    return lockManager.withLock(userId) {
        // 이 시점엔 이미 다른 스레드가 포인트를 변경했을 수 있음
        val updated = user.use(amount)  // 음수 포인트 발생 가능!
        saveHistory(...)
        updated
    }
}

// ✅ 좋은 예: 상태 조회/검증/변경을 Lock 안에서 원자적으로 수행
fun useUserPoint(userId: Long, amount: Long): UserPoint {
    // 입력값만 검증하는 경우 Lock 밖 가능 (선택적 최적화)
    // if (amount <= 0) throw IllegalArgumentException()

    return lockManager.withLock(userId) {
        val user = getUserPoint(userId)      // 1. 상태 조회
        user.validateSufficientPoints(amount)  // 2. 상태 기반 검증
        val updated = user.use(amount)       // 3. 상태 변경
        saveHistory(...)
        updated
    }
}
```

**Lock 밖으로 빼도 되는 것**:
- 상태와 무관한 입력값 검증 (`amount > 0`, `amount < MAX`)
- 로깅, 모니터링 등 부수 효과

**Lock 안에 있어야 하는 것**:
- 현재 상태 조회 (`getUserPoint()`)
- 상태 기반 검증 (`validateSufficientPoints()`)
- 상태 변경 (`insertOrUpdate()`)
- 히스토리 저장 (상태 변경과 함께 원자적으로 수행)

#### 3. 데드락 방지
- **단일 Lock 획득**: 하나의 요청은 하나의 사용자 Lock만 획득
- **Lock 순서 일관성**: 항상 같은 순서로 Lock 획득 (사용자 ID 기준)
- **타임아웃 설정 가능**: ReentrantLock의 `tryLock(timeout)` 활용 가능

---

## 구현 상세

### 1. Lock Manager 인터페이스

```kotlin
interface UserPointLockManager {
    fun <T> withLock(userId: Long, action: () -> T): T
}
```

**설계 의도**:
- **단일 책임 원칙**: Lock 관리 로직을 별도 컴포넌트로 분리
- **전략 패턴**: 다양한 Lock 구현체로 교체 가능
- **고차 함수 활용**: Kotlin의 람다로 깔끔한 API 제공

### 2. InMemoryUserPointLockManager 구현

```kotlin
@Component
class InMemoryUserPointLockManager : UserPointLockManager {
    private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()

    private fun getLock(userId: Long): ReentrantLock {
        return userLocks.computeIfAbsent(userId) { ReentrantLock() }
    }

    override fun <T> withLock(userId: Long, action: () -> T): T {
        val lock = getLock(userId)
        lock.lock()
        try {
            return action()
        } finally {
            lock.unlock()
        }
    }
}
```

#### 주요 구현 포인트

##### ConcurrentHashMap 사용 이유
```kotlin
// ❌ HashMap: Thread-safe하지 않음
private val userLocks = HashMap<Long, ReentrantLock>()

// ❌ Hashtable: 성능이 낮음 (모든 메서드가 synchronized)
private val userLocks = Hashtable<Long, ReentrantLock>()

// ✅ ConcurrentHashMap: Lock striping으로 높은 동시성 지원
private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()
```

##### computeIfAbsent의 원자성
```kotlin
// ❌ 나쁜 예: Race Condition 발생 가능
fun getLock(userId: Long): ReentrantLock {
    if (!userLocks.containsKey(userId)) {
        userLocks[userId] = ReentrantLock()  // Race Condition!
    }
    return userLocks[userId]!!
}

// ✅ 좋은 예: 원자적 연산
fun getLock(userId: Long): ReentrantLock {
    return userLocks.computeIfAbsent(userId) { ReentrantLock() }
}
```

##### try-finally로 Lock 해제 보장
```kotlin
// ❌ 나쁜 예: 예외 발생 시 Lock 해제 안 됨 → 데드락!
override fun <T> withLock(userId: Long, action: () -> T): T {
    val lock = getLock(userId)
    lock.lock()
    val result = action()  // 예외 발생 시?
    lock.unlock()
    return result
}

// ✅ 좋은 예: 예외 발생해도 반드시 Lock 해제
override fun <T> withLock(userId: Long, action: () -> T): T {
    val lock = getLock(userId)
    lock.lock()
    try {
        return action()
    } finally {
        lock.unlock()  // 항상 실행됨
    }
}
```

### 3. PointService에서의 활용

```kotlin
@Service
class PointService(
    private val userPointTable: UserPointTable,
    private val pointHistoryTable: PointHistoryTable,
    private val lockManager: UserPointLockManager,
) {
    fun chargeUserPoint(id: Long, amount: Long, currentTimeMillis: Long): UserPoint {
        return lockManager.withLock(id) {  // 사용자별 Lock
            val userPoint = userPointTable.selectById(id)
            userPoint.validatePositivePoint(amount)

            val updatedPoint = userPointTable.insertOrUpdate(id, userPoint.point + amount)
            saveHistory(id, amount, TransactionType.CHARGE, currentTimeMillis)

            updatedPoint
        }
    }

    fun useUserPoint(id: Long, amount: Long, currentTimeMillis: Long): UserPoint {
        return lockManager.withLock(id) {  // 사용자별 Lock
            val userPoint = userPointTable.selectById(id)
            userPoint.validateSufficientPoints(amount)

            val updatedPoint = userPointTable.insertOrUpdate(id, userPoint.point - amount)
            saveHistory(id, amount, TransactionType.USE, currentTimeMillis)

            updatedPoint
        }
    }
}
```

**동시성 제어 범위**:
1. ✅ **포인트 조회** → 읽기 전용이므로 Lock 불필요
2. ✅ **포인트 충전** → Lock으로 보호
3. ✅ **포인트 사용** → Lock으로 보호
4. ✅ **히스토리 조회** → 읽기 전용이므로 Lock 불필요

---

## 테스트 전략

### 통합 테스트 (PointServiceConcurrencyTest)

#### 1. 동일 사용자 동시 충전 테스트
```kotlin
@Test
fun `동일 사용자에 대한 동시 충전 요청이 순차적으로 처리된다`()
```
- **목적**: Race Condition 방지 검증
- **시나리오**: 10개 스레드가 동시에 100원씩 충전
- **검증**: 최종 포인트 = 1000원 (100 × 10)

#### 2. 동일 사용자 동시 사용 테스트
```kotlin
@Test
fun `동일 사용자에 대한 동시 사용 요청이 순차적으로 처리된다`()
```
- **목적**: 순차 처리 검증
- **시나리오**: 1000원에서 10개 스레드가 100원씩 사용
- **검증**: 최종 포인트 = 0원

#### 3. 충전/사용 혼합 동시 요청 테스트
```kotlin
@Test
fun `동일 사용자에 대한 충전과 사용이 동시에 발생해도 데이터 정합성이 유지된다`()
```
- **목적**: 복합 연산의 정합성 검증
- **시나리오**: 충전 10번(100원), 사용 10번(50원) 동시 실행
- **검증**: 최종 포인트 = 총 충전 - 총 사용

#### 4. 서로 다른 사용자 독립 처리 테스트
```kotlin
@Test
fun `서로 다른 사용자에 대한 동시 요청은 독립적으로 처리된다`()
```
- **목적**: 사용자별 Lock 격리 검증
- **시나리오**: 5명의 사용자가 각각 10번씩 충전
- **검증**: 각 사용자 포인트 = 1000원

#### 5. 포인트 부족 예외 처리 테스트
```kotlin
@Test
fun `동시성 환경에서 포인트 부족 예외가 정상적으로 동작한다`()
```
- **목적**: 동시성 환경에서 예외 처리 검증
- **시나리오**: 500원에서 10개 스레드가 100원씩 사용 시도
- **검증**: 성공 5번, 실패 5번, 최종 포인트 0원

---

## 성능 및 한계

### 성능 특성

#### 장점
1. **사용자별 격리**: 서로 다른 사용자 요청은 병렬 처리
   ```
   User 1: [========] 100ms
   User 2:  [========] 100ms  ← 동시 실행 가능
   User 3:   [========] 100ms  ← 동시 실행 가능
   총 소요 시간: 100ms (순차 실행 시 300ms)
   ```

2. **메모리 효율**: Lock은 사용자당 하나만 생성 (필요 시에만)

3. **Lock 오버헤드 최소화**: Critical Section을 최소화

#### 단점
1. **메모리 누수 가능성**
   ```kotlin
   // 문제: 한 번 생성된 Lock은 영구 보관됨
   private val userLocks = ConcurrentHashMap<Long, ReentrantLock>()
   ```
   - 사용자가 100만 명이면 100만 개의 Lock 객체
   - 해결책: LRU 캐시, 주기적 정리 등

2. **단일 서버 한정**
   - 분산 환경에서는 동작하지 않음
   - 서버 A와 서버 B가 각각 다른 Lock을 가짐

3. **공정성 미보장**
   ```kotlin
   // 현재: 비공정 모드 (기본값)
   ReentrantLock()

   // 공정 모드로 변경 가능 (FIFO 순서 보장, 단 성능 저하)
   ReentrantLock(true)
   ```

---
