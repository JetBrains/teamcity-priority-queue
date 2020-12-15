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
import jetbrains.buildServer.configuration.FileWatcher;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.CriticalErrorsImpl;
import jetbrains.buildServer.serverSide.impl.FileWatcherFactory;
import jetbrains.buildServer.serverSide.impl.persisting.SettingsPersister;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Level;
import org.jdom.Document;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.States;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.matcher.IsCollectionContainingMatcher.hasItem;
import static jetbrains.buildServer.serverSide.priority.BuildTypeMatcher.buildType;
import static jetbrains.buildServer.serverSide.priority.Util.getTestDataDir;
import static jetbrains.buildServer.serverSide.priority.Util.prepareBuildTypes;
import static jetbrains.buildServer.util.Util.map;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author dmitry.neverov
 */
@Test
public class PriorityClassManagerTest {

  private static final File PLUGIN_CONFIG_FILE = new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME);

  private final TempFiles myTempFiles = new TempFiles();
  private Mockery myContext;
  private BuildQueueEx myQueue;
  private BuildQueuePriorityOrdering myStrategy;
  private ServerListener myListener;
  private ProjectManager myProjectManager;
  private PriorityClassManagerImpl myPriorityClassManager;

  @SuppressWarnings("unchecked")
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws IOException {
    TestInternalProperties.init();

    new TestLogger().onSuiteStart();

    myContext = new Mockery(){{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    final SBuildServer server = myContext.mock(SBuildServer.class);
    final ServerPaths serverPaths = Util.getServerPaths(myTempFiles.createTempDir());
    final EventDispatcher<BuildServerListener> eventDispatcher = (EventDispatcher<BuildServerListener>) myContext.mock(EventDispatcher.class);
    myQueue = myContext.mock(BuildQueueEx.class);
    myProjectManager = myContext.mock(ProjectManager.class);
    SettingsPersister settingsPersister = myContext.mock(SettingsPersister.class);
    Loggers.SERVER.setLevel(Level.DEBUG);

    myContext.checking(new Expectations() {{
      allowing(server).getQueue(); will(returnValue(myQueue));
      allowing(server).getFullServerVersion(); will(returnValue("1.0"));
      allowing(server).getProjectManager(); will(returnValue(myProjectManager));
      allowing(myQueue).setOrderingStrategy(with(any(BuildQueueOrderingStrategy.class)));
      allowing(myQueue).getItems(); will(returnValue(Collections.<SQueuedBuild>emptyList()));
      allowing(eventDispatcher).addListener(with(any(BuildServerListener.class)));
      allowing(myProjectManager).getAllBuildTypes(); will(returnValue(Collections.<SBuildType>emptyList()));
      allowing(settingsPersister).scheduleSaveDocument(with(any(String.class)), with(any(FileWatcher.class)), with(any(Document.class)));
    }});

    FileWatcherFactory fwf = new FileWatcherFactory(serverPaths, new CriticalErrorsImpl(serverPaths));
    fwf.setEventDispatcher(eventDispatcher);
    fwf.serverStarted();
    myPriorityClassManager = new PriorityClassManagerImpl(server, serverPaths, eventDispatcher, fwf, settingsPersister);
    myStrategy = new BuildQueuePriorityOrdering(myQueue, myPriorityClassManager);
    myListener = new ServerListener(eventDispatcher, myQueue, myStrategy, myPriorityClassManager);
    myListener.serverStartup();
  }


  @AfterMethod(alwaysRun = true)
  public void tearDown() throws InterruptedException {
    FileUtil.delete(PLUGIN_CONFIG_FILE);
    myTempFiles.cleanup();
  }


  @Test
  public void test_cannot_change_default_priority_class() {
    PriorityClass defaultPriorityClass = myPriorityClassManager.getDefaultPriorityClass();
    String defaultName = defaultPriorityClass.getName();
    String defaultDescription = defaultPriorityClass.getDescription();
    int defaultPriority = defaultPriorityClass.getPriority();
    List<SBuildType> defaultBuildTypes = defaultPriorityClass.getBuildTypes();

    PriorityClass update = defaultPriorityClass.update("Usual Priority Class", "New description", 10);
    myPriorityClassManager.savePriorityClass(update);

    defaultPriorityClass = myPriorityClassManager.getDefaultPriorityClass();
    assertEquals(defaultName, defaultPriorityClass.getName());
    assertEquals(defaultDescription, defaultPriorityClass.getDescription());
    assertEquals(defaultPriority, defaultPriorityClass.getPriority());
    assertEquals(defaultBuildTypes, defaultPriorityClass.getBuildTypes());
  }

  @Test
  public void test_can_change_only_priority_of_personal_priority_class() {
    PriorityClass personalPriorityClass = myPriorityClassManager.getPersonalPriorityClass();
    String defaultName = personalPriorityClass.getName();
    String defaultDescription = personalPriorityClass.getDescription();
    List<SBuildType> defaultBuildTypes = personalPriorityClass.getBuildTypes();

    PriorityClass update = personalPriorityClass.update("Usual Priority Class", "New description", 10);
    myPriorityClassManager.savePriorityClass(update);

    personalPriorityClass = myPriorityClassManager.getPersonalPriorityClass();
    assertEquals(defaultName, personalPriorityClass.getName());
    assertEquals(defaultDescription, personalPriorityClass.getDescription());
    assertEquals(10, personalPriorityClass.getPriority());
    assertEquals(defaultBuildTypes, personalPriorityClass.getBuildTypes());
  }

  @Test
  public void test_change_personal_priority_class_priority() {
    PriorityClass personalPriorityClass = myPriorityClassManager.getPersonalPriorityClass();
    PriorityClass updatedPersonalPriorityClass = personalPriorityClass.setPriority(1);
    myPriorityClassManager.savePriorityClass(updatedPersonalPriorityClass);
    assertEquals(1, myPriorityClassManager.getPersonalPriorityClass().getPriority());
  }


  @Test
  public void test_cannot_delete_personal_priority_class() {
    myPriorityClassManager.deletePriorityClass(myPriorityClassManager.getPersonalPriorityClass().getId());
    assertNotNull(myPriorityClassManager.getPersonalPriorityClass());
  }


  @Test
  public void test_cannot_delete_default_priority_class() {
    myPriorityClassManager.deletePriorityClass(myPriorityClassManager.getDefaultPriorityClass().getId());
    assertNotNull(myPriorityClassManager.getDefaultPriorityClass());
  }


  @Test
  public void test_move_buildTypes_between_priorityClasses() {
    Map<String, SBuildType> id2bt = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3");
    Set<SBuildType> pc1buildTypes = new HashSet<SBuildType>();
    pc1buildTypes.addAll(id2bt.values());
    PriorityClass pc1 = myPriorityClassManager.createPriorityClass("pc1", "Priority class one", 5, pc1buildTypes);
    PriorityClass pc2 = myPriorityClassManager.createPriorityClass("pc2", "Priority class two", 0, Collections.<SBuildType>emptySet());

    Set<String> movedBuildTypes = new HashSet<String>();
    movedBuildTypes.add("bt1");
    movedBuildTypes.add("bt2");
    PriorityClass updatedPc2 = pc2.addBuildTypes(asList("bt1", "bt2"));
    myPriorityClassManager.savePriorityClass(updatedPc2);

    PriorityClass updatedPc1 = myPriorityClassManager.findPriorityClassById(pc1.getId());
    assertEquals(1, updatedPc1.getBuildTypes().size());
    assertEquals(updatedPc1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt3")));
    assertEquals(updatedPc2, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt1")));
    assertEquals(updatedPc2, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt2")));
  }


  public void test_remove_buildTypes() {
    Map<String, SBuildType> id2bt = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3");
    Set<String> pc1buildTypes = new HashSet<String>();
    for (SBuildType bt : id2bt.values()) {
      pc1buildTypes.add(bt.getBuildTypeId());
    }
    PriorityClass pc1 = myPriorityClassManager.createPriorityClass("pc1", "Priority class one", 5);

    pc1 = pc1.addBuildTypes(pc1buildTypes);
    myPriorityClassManager.savePriorityClass(pc1);

    assertEquals(pc1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt1")));
    assertEquals(pc1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt2")));
    assertEquals(pc1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt3")));

    pc1 = pc1.removeBuildTypes(asList("bt1", "bt2"));
    myPriorityClassManager.savePriorityClass(pc1);

    pc1 = myPriorityClassManager.findPriorityClassById(pc1.getId());
    assertEquals(1, pc1.getBuildTypes().size());

    PriorityClass defaultPriorityClass = myPriorityClassManager.getDefaultPriorityClass();
    assertEquals(defaultPriorityClass, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt1")));
    assertEquals(defaultPriorityClass, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt2")));
    assertEquals(pc1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt3")));

    prepareBuildTypes(myContext, myProjectManager, map("bt4", "bt4External"));
    SBuildType bt4 = myProjectManager.findBuildTypeByExternalId("bt4External");
    pc1 = pc1.addBuildTypes(asList(bt4.getBuildTypeId()));
    myPriorityClassManager.savePriorityClass(pc1);
    assertEquals(pc1.getId(), myPriorityClassManager.getBuildTypePriorityClass(bt4).getId());

    pc1 = pc1.removeBuildTypes(asList(bt4.getBuildTypeId()));
    myPriorityClassManager.savePriorityClass(pc1);
    assertEquals(myPriorityClassManager.getDefaultPriorityClass().getId(), myPriorityClassManager.getBuildTypePriorityClass(bt4).getId());
  }


  public void test_remove_priority_class() {
    prepareBuildTypes(myContext, myProjectManager, map("bt1", "bt1External", "bt2", "bt2External"));
    SBuildType bt1 = myProjectManager.findBuildTypeByExternalId("bt1External");
    SBuildType bt2 = myProjectManager.findBuildTypeByExternalId("bt2External");
    PriorityClass pc1 = myPriorityClassManager.createPriorityClass("pc1", "description", 5);
    pc1 = pc1.addBuildTypes(asList(bt1.getBuildTypeId(), bt2.getBuildTypeId()));
    myPriorityClassManager.savePriorityClass(pc1);

    assertEquals(pc1.getId(), myPriorityClassManager.getBuildTypePriorityClass(bt1).getId());
    assertEquals(pc1.getId(), myPriorityClassManager.getBuildTypePriorityClass(bt2).getId());

    myPriorityClassManager.deletePriorityClass(pc1.getId());

    assertEquals(myPriorityClassManager.getDefaultPriorityClass().getId(), myPriorityClassManager.getBuildTypePriorityClass(bt1).getId());
    assertEquals(myPriorityClassManager.getDefaultPriorityClass().getId(), myPriorityClassManager.getBuildTypePriorityClass(bt2).getId());
  }

  public void should_support_external_id_rename() {
    final States externalId = myContext.states("bt1-externalId-state").startsAs("oldId");

    final SBuildType buildType = myContext.mock(SBuildType.class);
    myContext.checking(new Expectations() {{
      allowing(buildType).getBuildTypeId(); will(returnValue("bt1"));
      allowing(buildType).getExternalId(); when(externalId.is("oldId")); will(returnValue("bt1External"));
      allowing(buildType).getExternalId(); when(externalId.is("newId")); will(returnValue("bt1NewExternal"));
      allowing(myProjectManager).findBuildTypeById("bt1"); will(returnValue(buildType));
      allowing(myProjectManager).findBuildTypeByExternalId("bt1External"); when(externalId.is("oldId")); will(returnValue(buildType));
      allowing(myProjectManager).findBuildTypeByExternalId("bt1External"); when(externalId.is("newId")); will(returnValue(null));
      allowing(myProjectManager).findBuildTypeByExternalId("bt1NewExternal"); when(externalId.is("oldId")); will(returnValue(null));
      allowing(myProjectManager).findBuildTypeByExternalId("bt1NewExternal"); when(externalId.is("newId")); will(returnValue(buildType));
      allowing(myProjectManager).getAllBuildTypes(); will(returnValue(asList(buildType)));
    }});

    SBuildType bt1 = myProjectManager.findBuildTypeByExternalId("bt1External");

    PriorityClass pc1 = myPriorityClassManager.createPriorityClass("pc1", "description", 5);
    pc1 = pc1.addBuildTypes(asList(bt1.getBuildTypeId()));
    myPriorityClassManager.savePriorityClass(pc1);

    assertEquals(pc1.getId(), myPriorityClassManager.getBuildTypePriorityClass(bt1).getId());
    assertThat(myPriorityClassManager.findPriorityClassById(pc1.getId()).getBuildTypes(), hasItem(buildType().withId("bt1")));

    externalId.become("newId");
    myPriorityClassManager.buildTypeExternalIdChanged(bt1, "bt1External", "bt1NewExternal");

    assertEquals(pc1.getId(), myPriorityClassManager.getBuildTypePriorityClass(bt1).getId());
    assertThat(myPriorityClassManager.findPriorityClassById(pc1.getId()).getBuildTypes(), hasItem(buildType().withId("bt1")));
  }
}
