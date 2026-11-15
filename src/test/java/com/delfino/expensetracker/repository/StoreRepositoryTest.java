package com.delfino.expensetracker.repository;

import com.delfino.expensetracker.BaseControllerTest;
import com.delfino.expensetracker.model.Store;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class StoreRepositoryTest extends BaseControllerTest {

    @Test
    void findMatchingStore_whenInputCityIsBlank_matchesStoreWithCity() {
        var user = createTestUser("alice", "pass");

        Store store = new Store();
        store.setUserId(user.getId());
        store.setName("Coffee House");
        store.setAddress("123 Main St");
        store.setCity("Singapore");
        store.setCountry("SG");
        store.setPostalCode("123456");
        storeRepository.save(store);

        Optional<Store> match = storeRepository.findMatchingStore(
                user.getId(),
                "Coffee House",
                "123 Main St",
                "",
                "SG",
                "123456");

        assertThat(match.get().getId()).isEqualTo(store.getId());
    }

    @Test
    void findMatchingStore_prefersExactCityBeforeBlankCityWhenBothMatch() {
        var user = createTestUser("bob", "pass");

        Store exactCity = new Store();
        exactCity.setUserId(user.getId());
        exactCity.setName("Market One");
        exactCity.setAddress("88 Side Rd");
        exactCity.setCity("Singapore");
        exactCity.setCountry("SG");
        exactCity.setPostalCode("654321");
        storeRepository.save(exactCity);

        Optional<Store> match = storeRepository.findMatchingStore(
                user.getId(),
                "Market One",
                "88 Side Rd",
                "Singapore",
                "SG",
                "654321");

        assertThat(match.get().getId()).isEqualTo(exactCity.getId());
        assertThat(match.get().getCity()).isEqualTo(exactCity.getCity());
    }
}

