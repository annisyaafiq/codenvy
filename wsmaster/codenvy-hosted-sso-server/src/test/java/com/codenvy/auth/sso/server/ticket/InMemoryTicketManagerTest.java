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
package com.codenvy.auth.sso.server.ticket;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.codenvy.api.dao.authentication.AccessTicket;
import com.codenvy.api.dao.authentication.TicketManager;
import java.util.Collections;
import org.testng.annotations.Test;

public class InMemoryTicketManagerTest {
  private static final String TOKEN = "123456789";
  String userId = "sdfsd345345";

  @Test
  public void shouldBeAbleToPutAndGetTicket() {
    TicketManager ticketManager = new InMemoryTicketManager();

    assertNull(ticketManager.getAccessTicket(TOKEN));

    AccessTicket expectedTicket = new AccessTicket(TOKEN, userId, "default");

    ticketManager.putAccessTicket(expectedTicket);

    AccessTicket actualTicket = ticketManager.getAccessTicket(TOKEN);

    assertEquals(actualTicket, expectedTicket);
  }

  @Test
  public void shouldBeAbleToGetTickets() {
    TicketManager ticketManager = new InMemoryTicketManager();

    assertEquals(ticketManager.getAccessTickets().size(), 0);

    AccessTicket ticket = new AccessTicket(TOKEN, userId, "default");

    ticketManager.putAccessTicket(ticket);

    assertEquals(ticketManager.getAccessTickets(), Collections.singleton(ticket));
  }

  @Test
  public void shouldBeAbleToRemoveTickets() {

    TicketManager ticketManager = new InMemoryTicketManager();

    ticketManager.putAccessTicket(new AccessTicket(TOKEN, userId, "default"));

    assertEquals(ticketManager.getAccessTickets().size(), 1);

    ticketManager.removeTicket(TOKEN);

    assertEquals(ticketManager.getAccessTickets().size(), 0);
  }
}
