/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jetbrains.buildServer.serverSide.priority.Util.createBuildType;
import static jetbrains.buildServer.serverSide.priority.Util.getTestDataDir;
import static org.testng.AssertJUnit.*;
import static org.testng.AssertJUnit.assertEquals;

/**
 * @author dmitry.neverov
 */
@Test
public class PriorityClassManagerTest {

  private static final File PLUGIN_CONFIG_FILE = new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME);

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
    final ServerPaths serverPaths = myContext.mock(ServerPaths.class);
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
      allowing(serverPaths).getConfigDir(); will(returnValue(getTestDataDir().getAbsolutePath()));
      allowing(eventDispatcher).addListener(with(any(BuildServerListener.class)));
      allowing(myProjectManager).getAllBuildTypes(); will(returnValue(Collections.<SBuildType>emptyList()));
    }});

    myPriorityClassManager = new PriorityClassManagerImpl(server, serverPaths, eventDispatcher, new FileWatcherFactory(serverPaths));
    myStrategy = new BuildQueuePriorityOrdering(myPriorityClassManager);
    myListener = new ServerListener(eventDispatcher, server, myStrategy, myPriorityClassManager);
    myListener.serverStartup();
  }


  @AfterMethod(alwaysRun = true)
  public void tearDown() throws InterruptedException {
    FileUtil.delete(PLUGIN_CONFIG_FILE);
  }


  @Test
  public void test_cannot_change_default_priority_class() {
    PriorityClass defaultPriorityClass = myPriorityClassManager.getDefaultPriorityClass();
    String defaultName = defaultPriorityClass.getName();
    String defaultDescription = defaultPriorityClass.getDescription();
    int defaultPriority = defaultPriorityClass.getPriority();
    List<SBuildType> defaultBuildTypes = defaultPriorityClass.getBuildTypes();

    PriorityClassImpl update = new PriorityClassImpl(defaultPriorityClass.getId(), "Usual Priority Class",
            "New description", 10, Collections.singleton(createBuildType(myContext, "bt1")));
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

    PriorityClassImpl update = new PriorityClassImpl(personalPriorityClass.getId(), "Usual Priority Class",
            "New description", 10, Collections.singleton(createBuildType(myContext, "bt1")));
    myPriorityClassManager.savePriorityClass(update);

    personalPriorityClass = myPriorityClassManager.getPersonalPriorityClass();
    assertEquals(defaultName, personalPriorityClass.getName());
    assertEquals(defaultDescription, personalPriorityClass.getDescription());
    assertEquals(10, personalPriorityClass.getPriority());
    assertEquals(defaultBuildTypes, personalPriorityClass.getBuildTypes());
  }

  @Test
  public void test_change_personal_priority_class_priority() {
    PriorityClassImpl personalPriorityClass = (PriorityClassImpl) myPriorityClassManager.getPersonalPriorityClass();
    PriorityClassImpl updatedPersonalPriorityClass = new PriorityClassImpl(personalPriorityClass.getId(), personalPriorityClass.getName(),
            personalPriorityClass.getDescription(), 1, personalPriorityClass.getBuildTypes());
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
    SBuildType bt1 = createBuildType(myContext, "bt1");
    SBuildType bt2 = createBuildType(myContext, "bt2");
    SBuildType bt3 = createBuildType(myContext, "bt3");
    Set<SBuildType> pc1buildTypes = new HashSet<SBuildType>();
    pc1buildTypes.add(bt1);
    pc1buildTypes.add(bt2);
    pc1buildTypes.add(bt3);
    PriorityClass pc1 = myPriorityClassManager.createPriorityClass("pc1", "Priority class one", 5, pc1buildTypes);
    PriorityClass pc2 = myPriorityClassManager.createPriorityClass("pc2", "Priority class two", 0, Collections.<SBuildType>emptySet());

    Set<SBuildType> movedBuildTypes = new HashSet<SBuildType>();
    movedBuildTypes.add(bt1);
    movedBuildTypes.add(bt2);
    PriorityClass updatedPc2 = new PriorityClassImpl(pc2.getId(), pc2.getName(), pc2.getDescription(), pc2.getPriority(), movedBuildTypes);
    myPriorityClassManager.savePriorityClass(updatedPc2);

    PriorityClass updatedPc1 = myPriorityClassManager.findPriorityClassById(pc1.getId());
    assertEquals(1, updatedPc1.getBuildTypes().size());
    assertEquals(updatedPc2, myPriorityClassManager.getBuildTypePriorityClass(bt1));
    assertEquals(updatedPc2, myPriorityClassManager.getBuildTypePriorityClass(bt2));
    assertEquals(updatedPc1, myPriorityClassManager.getBuildTypePriorityClass(bt3));
  }


  public void test_remove_buildTypes() {
    SBuildType bt1 = createBuildType(myContext, "bt1");
    SBuildType bt2 = createBuildType(myContext, "bt2");
    SBuildType bt3 = createBuildType(myContext, "bt3");
    Set<SBuildType> pc1buildTypes = new HashSet<SBuildType>();
    pc1buildTypes.add(bt1);
    pc1buildTypes.add(bt2);
    pc1buildTypes.add(bt3);
    PriorityClass pc1 = myPriorityClassManager.createPriorityClass("pc1", "Priority class one", 5);

    PriorityClass pc1Update1 = new PriorityClassImpl(pc1.getId(), pc1.getName(), pc1.getDescription(), pc1.getPriority(), pc1buildTypes);
    myPriorityClassManager.savePriorityClass(pc1Update1);

    assertEquals(pc1Update1, myPriorityClassManager.getBuildTypePriorityClass(bt1));
    assertEquals(pc1Update1, myPriorityClassManager.getBuildTypePriorityClass(bt2));
    assertEquals(pc1Update1, myPriorityClassManager.getBuildTypePriorityClass(bt3));

    PriorityClass pc1update2 = new PriorityClassImpl(pc1.getId(), pc1.getName(), pc1.getDescription(), pc1.getPriority(), Collections.singleton(bt3));
    myPriorityClassManager.savePriorityClass(pc1update2);

    pc1update2 = myPriorityClassManager.findPriorityClassById(pc1.getId());
    assertEquals(1, pc1update2.getBuildTypes().size());

    PriorityClass defaultPriorityClass = myPriorityClassManager.getDefaultPriorityClass();
    assertEquals(defaultPriorityClass, myPriorityClassManager.getBuildTypePriorityClass(bt1));
    assertEquals(defaultPriorityClass, myPriorityClassManager.getBuildTypePriorityClass(bt2));
    assertEquals(pc1update2, myPriorityClassManager.getBuildTypePriorityClass(bt3));
  }
}
