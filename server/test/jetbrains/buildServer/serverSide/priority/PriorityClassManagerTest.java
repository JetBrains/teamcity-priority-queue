/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import jetbrains.buildServer.TestLogger;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.FileWatcherFactory;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Level;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.Arrays.asList;
import static jetbrains.buildServer.serverSide.priority.Util.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

/**
 * @author dmitry.neverov
 */
@Test
public class PriorityClassManagerTest {

  private static final File PLUGIN_CONFIG_FILE = new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME);

  private TempFiles myTempFiles = new TempFiles();
  private Mockery myContext;
  private BuildQueueEx myQueue;
  private BuildQueuePriorityOrdering myStrategy;
  private ServerListener myListener;
  private ProjectManager myProjectManager;
  private PriorityClassManagerImpl myPriorityClassManager;

  @SuppressWarnings("unchecked")
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws IOException {
    new TestLogger().onSuiteStart();

    myContext = new Mockery(){{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    final SBuildServer server = myContext.mock(SBuildServer.class);
    final ServerPaths serverPaths = Util.getServerPaths(myTempFiles.createTempDir());
    final EventDispatcher<BuildServerListener> eventDispatcher = (EventDispatcher<BuildServerListener>) myContext.mock(EventDispatcher.class);
    myQueue = myContext.mock(BuildQueueEx.class);
    myProjectManager = myContext.mock(ProjectManager.class);
    Loggers.SERVER.setLevel(Level.DEBUG);

    myContext.checking(new Expectations() {{
      allowing(server).getQueue(); will(returnValue(myQueue));
      allowing(server).getFullServerVersion(); will(returnValue("1.0"));
      allowing(server).getProjectManager(); will(returnValue(myProjectManager));
      allowing(myQueue).setOrderingStrategy(with(any(BuildQueueOrderingStrategy.class)));
      allowing(myQueue).getItems(); will(returnValue(Collections.<SQueuedBuild>emptyList()));
      allowing(eventDispatcher).addListener(with(any(BuildServerListener.class)));
      allowing(myProjectManager).getAllBuildTypes(); will(returnValue(Collections.<SBuildType>emptyList()));
      allowing(myProjectManager).findBuildTypes(new HashSet<String>()); will(returnValue(Collections.<SBuildType>emptyList()));
    }});

    FileWatcherFactory fwf = new FileWatcherFactory(serverPaths);
    fwf.setCleanupManager(new Util.MockServerCleanupManager());
    myPriorityClassManager = new PriorityClassManagerImpl(server, serverPaths, eventDispatcher, fwf);
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

    PriorityClass update = defaultPriorityClass.update("Usual Priority Class", "New description", 10, asList(createBuildType(myContext, "bt1").getBuildTypeId()));
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

    PriorityClass update = personalPriorityClass.update("Usual Priority Class", "New description", 10, asList(createBuildType(myContext, "bt1").getBuildTypeId()));
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

    PriorityClass pc1Update1 = pc1.addBuildTypes(pc1buildTypes);
    myPriorityClassManager.savePriorityClass(pc1Update1);

    assertEquals(pc1Update1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt1")));
    assertEquals(pc1Update1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt2")));
    assertEquals(pc1Update1, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt3")));

    PriorityClass pc1update2 = pc1Update1.removeBuildTypes(asList("bt1", "bt2"));
    myPriorityClassManager.savePriorityClass(pc1update2);

    pc1update2 = myPriorityClassManager.findPriorityClassById(pc1.getId());
    assertEquals(1, pc1update2.getBuildTypes().size());

    PriorityClass defaultPriorityClass = myPriorityClassManager.getDefaultPriorityClass();
    assertEquals(defaultPriorityClass, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt1")));
    assertEquals(defaultPriorityClass, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt2")));
    assertEquals(pc1update2, myPriorityClassManager.getBuildTypePriorityClass(id2bt.get("bt3")));
  }
}
