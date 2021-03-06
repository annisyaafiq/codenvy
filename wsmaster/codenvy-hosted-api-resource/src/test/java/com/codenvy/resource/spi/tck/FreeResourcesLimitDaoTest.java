/*
 * Copyright (c) [2012] - [2017] Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.codenvy.resource.spi.tck;

import static java.util.Collections.singletonList;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.codenvy.resource.spi.FreeResourcesLimitDao;
import com.codenvy.resource.spi.impl.FreeResourcesLimitImpl;
import com.codenvy.resource.spi.impl.ResourceImpl;
import com.codenvy.resource.spi.jpa.JpaFreeResourcesLimitDao;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import org.eclipse.che.account.spi.AccountImpl;
import org.eclipse.che.api.core.NotFoundException;
import org.eclipse.che.api.core.Page;
import org.eclipse.che.commons.test.tck.TckListener;
import org.eclipse.che.commons.test.tck.repository.TckRepository;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * Tests for {@link JpaFreeResourcesLimitDao}
 *
 * @author Sergii Leschenko
 */
@Listeners(TckListener.class)
@Test(suiteName = FreeResourcesLimitDaoTest.SUITE_NAME)
public class FreeResourcesLimitDaoTest {
  public static final String SUITE_NAME = "FreeResourcesLimitDaoTck";

  private static final String TEST_RESOURCE_TYPE = "Test";
  private static final int COUNTS_OF_LIMITS = 3;

  private FreeResourcesLimitImpl[] limits;
  private AccountImpl[] accounts;

  @Inject private TckRepository<FreeResourcesLimitImpl> limitRepository;

  @Inject private TckRepository<AccountImpl> accountRepository;

  @Inject private FreeResourcesLimitDao limitDao;

  @BeforeMethod
  private void setUp() throws Exception {
    accounts = new AccountImpl[COUNTS_OF_LIMITS];
    limits = new FreeResourcesLimitImpl[COUNTS_OF_LIMITS];
    for (int i = 0; i < COUNTS_OF_LIMITS; i++) {
      accounts[i] = new AccountImpl("accountId-" + i, "accountName" + i, "test");
      limits[i] =
          new FreeResourcesLimitImpl(
              accounts[i].getId(), singletonList(new ResourceImpl(TEST_RESOURCE_TYPE, i, "test")));
    }
    accountRepository.createAll(Arrays.asList(accounts));
    limitRepository.createAll(
        Stream.of(limits).map(FreeResourcesLimitImpl::new).collect(Collectors.toList()));
  }

  @AfterMethod
  private void cleanup() throws Exception {
    limitRepository.removeAll();
    accountRepository.removeAll();
  }

  @Test
  public void shouldCreateNewResourcesLimitWhenStoringNotExistentOne() throws Exception {
    //given
    FreeResourcesLimitImpl toStore = limits[0];
    limitDao.remove(toStore.getAccountId());

    //when
    limitDao.store(toStore);

    //then
    assertEquals(limitDao.get(toStore.getAccountId()), new FreeResourcesLimitImpl(toStore));
  }

  @Test
  public void shouldUpdateResourcesLimitWhenStoringExistentOne() throws Exception {
    //given
    FreeResourcesLimitImpl toStore =
        new FreeResourcesLimitImpl(
            limits[0].getAccountId(),
            singletonList(new ResourceImpl(TEST_RESOURCE_TYPE, 1000, "unit")));

    //when
    limitDao.store(toStore);

    //then
    assertEquals(limitDao.get(toStore.getAccountId()), new FreeResourcesLimitImpl(toStore));
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrowNpeWhenStoringNullableResourcesLimit() throws Exception {
    //when
    limitDao.store(null);
  }

  @Test
  public void shouldGetResourcesLimitForSpecifiedAccountId() throws Exception {
    //given
    FreeResourcesLimitImpl stored = limits[0];

    //when
    FreeResourcesLimitImpl fetched = limitDao.get(stored.getAccountId());

    //then
    assertEquals(fetched, stored);
  }

  @Test(expectedExceptions = NotFoundException.class)
  public void shouldThrowNotFoundExceptionWhenGettingNonExistentResourcesLimit() throws Exception {
    //when
    limitDao.get("account123");
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrowNpeWhenGettingFreeResourcesLimitByNullAccountId() throws Exception {
    //when
    limitDao.get(null);
  }

  @Test
  public void shouldGetAllLimits() throws Exception {
    //when
    final Page<FreeResourcesLimitImpl> children = limitDao.getAll(1, 1);

    //then
    assertEquals(children.getTotalItemsCount(), 3);
    assertEquals(children.getItemsCount(), 1);
    assertTrue(
        children.getItems().contains(limits[0])
            ^ children.getItems().contains(limits[1])
            ^ children.getItems().contains(limits[2]));
  }

  @Test(expectedExceptions = NotFoundException.class)
  public void shouldRemoveResourcesLimit() throws Exception {
    //given
    FreeResourcesLimitImpl existedLimit = limits[0];

    //when
    limitDao.remove(existedLimit.getAccountId());

    //then
    limitDao.get(existedLimit.getAccountId());
  }

  @Test(expectedExceptions = NullPointerException.class)
  public void shouldThrowNpeWhenRemovingFreeResourcesLimitByNullId() throws Exception {
    //when
    limitDao.remove(null);
  }
}
