package com.cartmate.cartmate.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

public class OrderServiceClassesOnly implements ImportOption {

    @Override
    public boolean includes(Location location) {
        return location.contains("/order-service/");
    }
}
