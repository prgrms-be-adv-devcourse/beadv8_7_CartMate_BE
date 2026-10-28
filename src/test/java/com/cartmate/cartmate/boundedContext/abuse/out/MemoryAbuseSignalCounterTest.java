package com.cartmate.cartmate.boundedContext.abuse.out;

class MemoryAbuseSignalCounterTest extends AbuseSignalCounterContractTest {

    @Override
    protected AbuseSignalCounter createCounter() {
        return new MemoryAbuseSignalCounter();
    }
}
