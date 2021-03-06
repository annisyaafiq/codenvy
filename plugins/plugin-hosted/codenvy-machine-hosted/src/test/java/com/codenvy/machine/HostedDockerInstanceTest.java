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
package com.codenvy.machine;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.eclipse.che.api.core.model.machine.Machine;
import org.eclipse.che.api.core.model.machine.MachineConfig;
import org.eclipse.che.api.core.model.machine.MachineStatus;
import org.eclipse.che.api.core.util.LineConsumer;
import org.eclipse.che.api.machine.server.exception.MachineException;
import org.eclipse.che.api.machine.server.model.impl.MachineConfigImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineLimitsImpl;
import org.eclipse.che.api.machine.server.model.impl.MachineSourceImpl;
import org.eclipse.che.commons.test.mockito.answer.WaitingAnswer;
import org.eclipse.che.plugin.docker.client.DockerConnector;
import org.eclipse.che.plugin.docker.client.DockerConnectorProvider;
import org.eclipse.che.plugin.docker.client.params.CommitParams;
import org.eclipse.che.plugin.docker.machine.DockerInstanceProcessesCleaner;
import org.eclipse.che.plugin.docker.machine.DockerInstanceRuntimeInfo;
import org.eclipse.che.plugin.docker.machine.DockerInstanceStopDetector;
import org.eclipse.che.plugin.docker.machine.DockerMachineFactory;
import org.eclipse.che.plugin.docker.machine.node.DockerNode;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.testng.MockitoTestNGListener;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/** @author Max Shaposhnik */
@Listeners(MockitoTestNGListener.class)
public class HostedDockerInstanceTest {
  private static final String CONTAINER = "container144";
  private static final String OWNER = "owner12";
  private static final String IMAGE = "image12";
  private static final String MACHINE_ID = "machine12";
  private static final String WORKSPACE_ID = "workspace12";
  private static final String NAME = "suse-jdk";
  private static final String TYPE = "docker";
  private static final String REGISTRY = "registry";
  private static final String USERNAME = "username";
  private static final String TAG = "latest";
  private static final int CONCURRENCY = 2;
  private static final MachineStatus STATUS = MachineStatus.RUNNING;
  @Mock private DockerConnectorProvider dockerConnectorProviderMock;
  @Mock private DockerConnector dockerConnectorMock;
  @Mock private DockerInstanceStopDetector dockerInstanceStopDetectorMock;
  @Mock private LineConsumer outputConsumer;
  @Mock private DockerNode dockerNode;

  private ExecutorService executor;

  private HostedDockerInstance dockerInstance;

  private HostedDockerInstance getDockerInstance() throws MachineException {
    DockerMachineFactory machineFactory = mock(DockerMachineFactory.class);
    when(machineFactory.createMetadata(any(), any(), any()))
        .thenReturn(mock(DockerInstanceRuntimeInfo.class));
    return new HostedDockerInstance(
        dockerConnectorProviderMock,
        REGISTRY,
        USERNAME,
        machineFactory,
        getMachine(getMachineConfig(true, NAME, TYPE), OWNER, MACHINE_ID, WORKSPACE_ID, STATUS),
        CONTAINER,
        IMAGE,
        dockerNode,
        outputConsumer,
        dockerInstanceStopDetectorMock,
        mock(DockerInstanceProcessesCleaner.class),
        false,
        CONCURRENCY);
  }

  @BeforeMethod
  public void setUp() throws IOException, MachineException {
    when(dockerNode.getHost()).thenReturn("host1");
    when(dockerConnectorProviderMock.get()).thenReturn(dockerConnectorMock);
    dockerInstance = Mockito.spy(getDockerInstance());
    executor = Executors.newFixedThreadPool(3);
  }

  @Test
  public void shouldBeAbleToCommitSimultaneously() throws Exception {

    String repo1 = "repo1";
    String repo2 = "repo2";
    String repo3 = "repo3";

    WaitingAnswer<Void> waitingAnswer1 = new WaitingAnswer<>();
    WaitingAnswer<Void> waitingAnswer2 = new WaitingAnswer<>();

    doAnswer(waitingAnswer1)
        .when(dockerConnectorMock)
        .commit(Matchers.argThat(new CommitParamsMatcher(repo1)));
    doAnswer(waitingAnswer2)
        .when(dockerConnectorMock)
        .commit(Matchers.argThat(new CommitParamsMatcher(repo2)));

    // Starting threads #1 & 2
    executor.execute(() -> performCommit(repo1, TAG));
    executor.execute(() -> performCommit(repo2, TAG));
    waitingAnswer1.waitAnswerCall(1, TimeUnit.SECONDS);
    waitingAnswer2.waitAnswerCall(1, TimeUnit.SECONDS);

    // when
    executor.execute(() -> performCommit(repo3, TAG));

    Thread.sleep(200); //to allow thread # 3 start

    // thread #3 is entered  method but should wait - semaphore is red
    verify(dockerInstance).commitContainer(eq(repo3), eq(TAG));
    verify(dockerConnectorMock, never()).commit(Matchers.argThat(new CommitParamsMatcher(repo3)));

    // completing first 2 calls
    waitingAnswer1.completeAnswer();
    waitingAnswer2.completeAnswer();

    // then
    awaitFinalization();

    verify(dockerConnectorMock).commit(Matchers.argThat(new CommitParamsMatcher(repo1)));
    verify(dockerConnectorMock).commit(Matchers.argThat(new CommitParamsMatcher(repo2)));
    verify(dockerConnectorMock).commit(Matchers.argThat(new CommitParamsMatcher(repo3)));
  }

  @Test
  public void shouldBeAbleToCommitSimultaneouslyOnDifferentNodes() throws Exception {

    String repo1 = "repo1";
    String repo2 = "repo2";
    String repo3 = "repo3";

    WaitingAnswer<Void> waitingAnswer1 = new WaitingAnswer<>();
    WaitingAnswer<Void> waitingAnswer2 = new WaitingAnswer<>();

    doAnswer(waitingAnswer1)
        .when(dockerConnectorMock)
        .commit(Matchers.argThat(new CommitParamsMatcher(repo1)));
    doAnswer(waitingAnswer2)
        .when(dockerConnectorMock)
        .commit(Matchers.argThat(new CommitParamsMatcher(repo2)));

    // Starting threads #1 & 2
    executor.execute(() -> performCommit(repo1, TAG));
    executor.execute(() -> performCommit(repo2, TAG));
    waitingAnswer1.waitAnswerCall(1, TimeUnit.SECONDS);
    waitingAnswer2.waitAnswerCall(1, TimeUnit.SECONDS);

    // thread #3 run on other node
    when(dockerNode.getHost()).thenReturn("host2");
    // when
    executor.execute(() -> performCommit(repo3, TAG));

    // thread #3 commit executed too
    verify(dockerInstance, timeout(2000)).commitContainer(eq(repo3), eq(TAG));
    ArgumentCaptor<CommitParams> paramsCaptor = ArgumentCaptor.forClass(CommitParams.class);
    verify(dockerConnectorMock, atLeastOnce()).commit(paramsCaptor.capture());
    assertEquals(paramsCaptor.getValue().getRepository(), repo3);

    // completing first 2 calls
    waitingAnswer1.completeAnswer();
    waitingAnswer2.completeAnswer();

    // then
    awaitFinalization();
  }

  private Machine getMachine(
      MachineConfig config, String owner, String machineId, String wsId, MachineStatus status) {
    return MachineImpl.builder()
        .setConfig(config)
        .setId(machineId)
        .setOwner(owner)
        .setWorkspaceId(wsId)
        .setEnvName("env")
        .setStatus(status)
        .build();
  }

  private MachineConfig getMachineConfig(boolean isDev, String name, String type) {
    return MachineConfigImpl.builder()
        .setDev(isDev)
        .setName(name)
        .setType(type)
        .setSource(new MachineSourceImpl("docker").setLocation("location"))
        .setLimits(new MachineLimitsImpl(64))
        .build();
  }

  private void performCommit(String repo, String tag) {
    try {
      dockerInstance.commitContainer(repo, tag);
    } catch (IOException ignore) {
    }
  }

  private void awaitFinalization() throws Exception {
    executor.shutdown();
    if (!executor.awaitTermination(1_000, TimeUnit.MILLISECONDS)) {
      fail("Operation is hanged up. Terminated.");
    }
  }

  private class CommitParamsMatcher extends ArgumentMatcher<CommitParams> {

    private final String compareValue;

    public CommitParamsMatcher(String compareValue) {
      this.compareValue = compareValue;
    }

    @Override
    public boolean matches(Object argument) {
      CommitParams item = (CommitParams) argument;
      return item.getRepository().equals(compareValue);
    }
  }
}
