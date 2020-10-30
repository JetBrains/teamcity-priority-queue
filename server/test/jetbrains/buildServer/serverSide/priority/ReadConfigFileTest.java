/*
 * Copyright 2000-2020 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.serverSide.priority;

import java.io.File;
import java.io.IOException;
import java.util.*;
import jetbrains.buildServer.TempFiles;
import jetbrains.buildServer.TestInternalProperties;
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.CriticalErrorsImpl;
import jetbrains.buildServer.serverSide.impl.FileWatcherFactory;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.States;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static jetbrains.buildServer.matcher.IsCollectionContainingMatcher.hasItem;
import static jetbrains.buildServer.serverSide.priority.BuildTypeMatcher.buildType;
import static jetbrains.buildServer.serverSide.priority.Util.getTestDataDir;
import static jetbrains.buildServer.serverSide.priority.Util.prepareBuildTypes;
import static jetbrains.buildServer.util.CollectionsUtil.setOf;
import static jetbrains.buildServer.util.Util.map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.AssertJUnit.*;

/**
 * @author dmitry.neverov
 */
@Test
public class ReadConfigFileTest {

  private final TempFiles myTempFiles = new TempFiles();
  private Mockery myContext;
  private SBuildServer myServer;
  private ServerPaths myServerPaths;
  private EventDispatcher<BuildServerListener> myEventDispatcher;
  private BuildQueueEx myQueue;
  private ProjectManager myProjectManager;

  @BeforeMethod(alwaysRun = true)
  public void setUp() throws IOException {
    TestInternalProperties.init();

    new TestLogger().onSuiteStart();
    Loggers.SERVER.setLevel(Level.DEBUG);

    myContext = new Mockery(){{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    myServer = myContext.mock(SBuildServer.class);
    myServerPaths = Util.getServerPaths(myTempFiles.createTempDir());
    myEventDispatcher = (EventDispatcher<BuildServerListener>) myContext.mock(EventDispatcher.class);
    myQueue = myContext.mock(BuildQueueEx.class);
    myProjectManager = myContext.mock(ProjectManager.class);

    myContext.checking(new Expectations() {{
      allowing(myServer).getQueue(); will(returnValue(myQueue));
      allowing(myServer).getFullServerVersion(); will(returnValue("1.0"));
      allowing(myServer).getProjectManager(); will(returnValue(myProjectManager));
      allowing(myQueue).setOrderingStrategy(with(any(BuildQueueOrderingStrategy.class)));
      allowing(myQueue).getItems(); will(returnValue(Collections.<Object>emptyList()));
      allowing(myEventDispatcher).addListener(with(any(BuildServerListener.class)));
    }});
  }

  @AfterMethod(alwaysRun = true)
  public void tearDown() throws InterruptedException {
    FileUtil.delete(new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME));
    myTempFiles.cleanup();
  }

  public void test() throws IOException {
    PriorityClassManager priorityClassManager = createPriorityClassManagerForConfig(new File(getTestDataDir(), "build-queue-priorities-sample.xml"));
    PriorityClass pc1 = priorityClassManager.findPriorityClassById("pc1");
    assertNotNull(pc1);
    assertEquals("pc1", pc1.getId());
    assertEquals("Inspections", pc1.getName());
    assertEquals("Low priority inspections", pc1.getDescription());
    assertEquals(-1, pc1.getPriority());
    assertEquals(2, pc1.getBuildTypes().size());
    Set<String> btIds1 = new HashSet<String>();
    for (SBuildType bt: pc1.getBuildTypes()) {
      btIds1.add(bt.getBuildTypeId());
    }
    assertTrue(btIds1.contains("bt14"));
    assertTrue(btIds1.contains("bt47"));

    PriorityClass pc2 = priorityClassManager.findPriorityClassById("pc2");
    assertNotNull(pc2);
    assertEquals("pc2", pc2.getId());
    assertEquals("Release", pc2.getName());
    assertEquals("Soon to be released", pc2.getDescription());
    assertEquals(5, pc2.getPriority());
    assertEquals(2, pc2.getBuildTypes().size());
    Set<String> btIds2 = new HashSet<String>();
    for (SBuildType bt: pc2.getBuildTypes()) {
      btIds2.add(bt.getBuildTypeId());
    }
    assertTrue(btIds2.contains("bt1"));
    assertTrue(btIds2.contains("bt3"));


    PriorityClass pc3 = priorityClassManager.createPriorityClass("New Priority Class", "", 0);
    assertEquals("priorityClassIdSequence was not corrected by config", "pc3", pc3.getId());
  }

  @DataProvider(name = "priorityConfigs")
  public Object[][] priorityConfigs() {
    return new Object[][] {
            {new File(getTestDataDir(), "build-queue-priorities-empty.xml")},
            {null},
            {new File(getTestDataDir(), "build-queue-priorities-invalid.xml")},
            {new File(getTestDataDir(), "build-queue-priorities-with-default.xml")}
    };
  }

  @Test(dataProvider = "priorityConfigs")
  public void test_server_startup(File prioritiesConfig) throws IOException {
    createPriorityClassManagerForConfig(prioritiesConfig);
  }


  public void test_priority_classes_not_deleted_after_read_invalid_config() throws IOException {
    PriorityClassManager priorityClassManager = createPriorityClassManagerForConfig(null);

    PriorityClass inspections = priorityClassManager.createPriorityClass("Inspections", "", -1);
    PriorityClass duplicates = priorityClassManager.createPriorityClass("Duplicates", "", -2);

    PriorityClass personals = priorityClassManager.getPersonalPriorityClass();
    PriorityClass updatePersonals = personals.setPriority(5);
    priorityClassManager.savePriorityClass(updatePersonals);

    //read invalid config
    FileUtil.copy(new File(getTestDataDir(), "build-queue-priorities-invalid.xml"),
            new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME));
    ((PriorityClassManagerImpl)priorityClassManager).loadPriorityClasses();

    inspections = priorityClassManager.findPriorityClassByName("Inspections");
    assertNotNull(inspections);

    duplicates = priorityClassManager.findPriorityClassByName("Duplicates");
    assertNotNull(duplicates);

    assertNotNull(priorityClassManager.getDefaultPriorityClass());
    assertNotNull(priorityClassManager.getPersonalPriorityClass());
  }


  public void test_can_change_only_priority_of_personal_priority_class() throws IOException {
    PriorityClassManager priorityClassManager = createPriorityClassManagerForConfig(null);

    PriorityClass personal = priorityClassManager.getPersonalPriorityClass();
    String personalName = personal.getName();
    String personalDescription = personal.getDescription();
    List<SBuildType> personalBuildTypes = personal.getBuildTypes();

    FileUtil.copy(new File(getTestDataDir(), "build-queue-priorities-with-personal.xml"),
                  new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME));
    ((PriorityClassManagerImpl)priorityClassManager).loadPriorityClasses();

    personal = priorityClassManager.getPersonalPriorityClass();
    assertEquals(personalName, personal.getName());
    assertEquals(personalDescription, personal.getDescription());
    assertEquals(personalBuildTypes, personal.getBuildTypes());
    assertEquals(10, personal.getPriority());
  }


  /** TW-13864 */
  public void test_removed_build_type() throws IOException {
    final States buildTypeState = myContext.states("bt6-state").startsAs("removed");
    final SBuildType bt6 = Util.createBuildType(myContext, "bt6", "bt6");

    myContext.checking(new Expectations() {{
      allowing(myProjectManager).findBuildTypeById("bt6"); when(buildTypeState.is("removed")); will(returnValue(null));
      allowing(myProjectManager).findBuildTypeByExternalId("bt6"); when(buildTypeState.is("removed")); will(returnValue(null));
      allowing(myProjectManager).findBuildTypeById("bt6"); when(buildTypeState.is("recovered")); will(returnValue(bt6));
      allowing(myProjectManager).findBuildTypeByExternalId("bt6"); when(buildTypeState.is("recovered")); will(returnValue(bt6));
    }});
    File prioritiesConfig = new File(getTestDataDir(), "build-queue-priorities-removed-build-type.xml");
    PriorityClassManager priorityClassManager = createPriorityClassManagerForConfig(prioritiesConfig);

    assertEquals(3, priorityClassManager.getAllPriorityClasses().size()); //no priority class is lost

    priorityClassManager.createPriorityClass("New priority class", "description", 10);//this makes priority class manager to persist config

    //emulate server restart (reread config):
    FileWatcherFactory fwf = new FileWatcherFactory(myServerPaths, new CriticalErrorsImpl(myServerPaths));
    fwf.setEventDispatcher(myEventDispatcher);
    fwf.serverStarted();
    priorityClassManager = new PriorityClassManagerImpl(myServer, myServerPaths, myEventDispatcher, fwf);
    ((PriorityClassManagerImpl) priorityClassManager).init();

    buildTypeState.become("recovered");

    PriorityClass pc = priorityClassManager.getBuildTypePriorityClass(bt6);
    assertEquals("pc1", pc.getId());
    PriorityClass pc1 = priorityClassManager.findPriorityClassById("pc1");
    assertEquals(1, pc1.getBuildTypes().size());
    assertEquals(bt6, pc1.getBuildTypes().get(0));
  }


  public void should_support_external_ids() throws Exception {
    //prepare 2 build types with external ids != internal ids
    prepareBuildTypes(myContext, myProjectManager, map("bt14", "bt14ExternalId", "bt47", "bt47ExternalId"));

    //load config with external ids
    FileUtil.copy(new File(getTestDataDir(), "build-queue-priorities-external-id.xml"),
                  new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME));
    FileWatcherFactory fwf = new FileWatcherFactory(myServerPaths, new CriticalErrorsImpl(myServerPaths));
    fwf.setEventDispatcher(myEventDispatcher);
    PriorityClassManagerImpl pcm = new PriorityClassManagerImpl(myServer, myServerPaths, myEventDispatcher, fwf);
    pcm.init();

    SBuildType bt14 = myProjectManager.findBuildTypeByExternalId("bt14ExternalId");
    SBuildType bt47 = myProjectManager.findBuildTypeByExternalId("bt47ExternalId");

    //ensure build types have right priority classes
    assertEquals("pc1", pcm.getBuildTypePriorityClass(bt14).getId());
    assertEquals("pc1", pcm.getBuildTypePriorityClass(bt47).getId());
    PriorityClass pc1 = pcm.findPriorityClassById("pc1");
    assertThat(pc1.getBuildTypes(), hasItem(buildType().withId("bt14")));
    assertThat(pc1.getBuildTypes(), hasItem(buildType().withId("bt47")));

    //create priority class, add build types
    prepareBuildTypes(myContext, myProjectManager, map("bt2", "bt2ExternalId", "bt3", "bt3ExternalId"));
    SBuildType bt2 = myProjectManager.findBuildTypeByExternalId("bt2ExternalId");
    SBuildType bt3 = myProjectManager.findBuildTypeByExternalId("bt3ExternalId");
    pcm.createPriorityClass("pc2", "description", 10, setOf(bt2, bt3));

    //reread config
    pcm = new PriorityClassManagerImpl(myServer, myServerPaths, myEventDispatcher, fwf);
    pcm.init();

    //ensure build types have right priority classes
    assertEquals("pc1", pcm.getBuildTypePriorityClass(bt14).getId());
    assertEquals("pc1", pcm.getBuildTypePriorityClass(bt47).getId());
    pc1 = pcm.findPriorityClassById("pc1");
    assertThat(pc1.getBuildTypes(), hasItem(buildType().withId("bt14")));
    assertThat(pc1.getBuildTypes(), hasItem(buildType().withId("bt47")));

    assertEquals("pc2", pcm.getBuildTypePriorityClass(bt2).getId());
    assertEquals("pc2", pcm.getBuildTypePriorityClass(bt3).getId());
    pc1 = pcm.findPriorityClassById("pc2");
    assertThat(pc1.getBuildTypes(), hasItem(buildType().withId("bt2")));
    assertThat(pc1.getBuildTypes(), hasItem(buildType().withId("bt3")));
  }


  private PriorityClassManager createPriorityClassManagerForConfig(@Nullable File prioritiesConfig) throws IOException {
    if (prioritiesConfig != null) {
      FileUtil.copy(prioritiesConfig, new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME));
    } else {
      FileUtil.delete(new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME));
    }

    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt14", "bt47", "bt1", "bt3", "bt5");

    FileWatcherFactory fwf = new FileWatcherFactory(myServerPaths, new CriticalErrorsImpl(myServerPaths));
    fwf.setEventDispatcher(myEventDispatcher);
    PriorityClassManagerImpl priorityClassManager = new PriorityClassManagerImpl(myServer, myServerPaths, myEventDispatcher, fwf);
    BuildQueuePriorityOrdering strategy = new BuildQueuePriorityOrdering(myQueue, priorityClassManager);
    ServerListener listener = new ServerListener(myEventDispatcher, myQueue, strategy, priorityClassManager);
    listener.serverStartup();

    PriorityClass defaultPriorityClass = priorityClassManager.getDefaultPriorityClass();
    assertNotNull(defaultPriorityClass);
    assertEquals(0, defaultPriorityClass.getPriority());

    PriorityClass personal = priorityClassManager.getPersonalPriorityClass();
    assertNotNull(personal);

    //buildType not in any priorityClass get default priority - 0
    assertEquals(0, priorityClassManager.getBuildTypePriorityClass(id2buildType.get("bt5")).getPriority());

    return priorityClassManager;
  }
}
