import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/*
 * Semaphore DOCS : https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Semaphore.html
 *
 * 세마포어의 permits, 스레드의 weight, 스레드 풀의 nThreads 값에 따라 운용될 스레드와 병렬 처리될 태스크가 변화하는 예제
 *
 * */
class SemaphoreEx {

    static final int MAX_AVAILABLE = 10;
    static final int MAX_THREAD = 5;
    static final int WEIGHT_TREAD = 2;
    static SharedResource sharedResource = new SharedResource(0);

    public static void main(String[] args) {

        /*
         * 스레드 풀
         * 고정된 개수 스레드 생성 및 운용 - 생성 오버헤드 감소, 재사용성, 과도한 병렬성 방지
         * FixedThreadPool : 고정된 개수의 스레드를 유지하는 스레드 풀
         * CachedThreadPool : 필요에 따라 스레드를 동적으로 생성하고 재사용하는 스레드 풀
         * SingleThreadExecutor : 하나의 스레드만을 사용하는 스레드 풀
         * ScheduledThreadPool : 특정 시간에 작업을 주기적으로 실행할 수 있는 스레드 풀
         * */
        ExecutorService executor = Executors.newFixedThreadPool(MAX_THREAD);
        MyRunnable myRunnable = new MyRunnable(sharedResource, WEIGHT_TREAD);

        for (int i = 0; i < 10; i++) {
            executor.execute(new Thread(myRunnable));
        }

        executor.shutdown();

        // 편법 - 런타임 종료시 태스크 == 스레드 == 훅 추가
        // 걍 쓰면 작업 스레드 보다 빨리 출력됨
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> System.out.println("main process finished")));
    }
}

class SharedResource {

    private int count;
    final Semaphore semaphore;

    SharedResource(int initialCount) {
        this.count = initialCount;
        this.semaphore = new Semaphore(SemaphoreEx.MAX_AVAILABLE, true) {

            /*
             * Semaphore(int permits, boolean fair)
             * 세마포어의 permits가 채워지면, 나머지 스레드는 대기 큐에 저장된다.
             * 이때 fair의 값에 따라 FIFO 방식을 사용할지 안할지 결정된다.
             * 대기 큐의 구현 컬렉션은 List이며, FIFO(fair=true) 방식을 사용할 시 Queue를 사용한다.
             *
             * 세마포어의 getQueuedThreads() 메서드로 대기 큐의 스레드 프로퍼티의 정보를 확인할 수 있다.
             * (정확히는 AbstractQueuedSynchronizer class에 있다.)
             * 다만, protected 선언되어있어서 오버라이딩해도 내 터미널에 출력할 수가 없었다.
             * 세마포어 내부 참조용으로만 쓰이는 듯 하다. (확실하지 않음)
             * */
            @Override
            public Collection<Thread> getQueuedThreads() {
                return super.getQueuedThreads().stream()
                        .collect(Collectors.toUnmodifiableList());
            }
        };
    }

    public void increment(int weight) throws InterruptedException {
        this.semaphore.acquire(weight);
        count += weight;
    }

    public void decrement(int weight) {
        this.semaphore.release(weight);
        count -= weight;
    }

    public int getCount() {
        return this.count;
    }

    public void printToString() {
        System.out.println("Permits : " + this.semaphore.availablePermits() + "\n" +
                "Now : " + this.getCount() + "\n" +
                "Thread : " + Thread.currentThread() + "\n" +
                "Thread Queue Length : " + this.semaphore.getQueueLength() + "\n");
//                .append(this.semaphore.getQueuedThreads).append("\n").toString();
    }

}

class MyRunnable implements Runnable {

    final SharedResource sharedResource;

    int weight;

    MyRunnable(SharedResource sharedResource, int weight) {
        this.sharedResource = sharedResource;
        this.weight = weight;
    }

    @Override
    public void run() {
        Thread thread = Thread.currentThread();

        try {
//            System.out.println("MyRunnable is running in a separate thread.");
            System.out.printf("%s\n\n", thread.getName());
//            System.out.println(thread.getId());
//            System.out.println(thread.getPriority());
//            System.out.println();
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        try {
            sharedResource.increment(weight);
            this.sharedResource.printToString();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            sharedResource.decrement(weight);
        }
    }
}
