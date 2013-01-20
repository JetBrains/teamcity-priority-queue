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

import static jetbrains.buildServer.serverSide.priority.Util.getTestDataDir;
import static jetbrains.buildServer.serverSide.priority.Util.prepareBuildTypes;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author dmitry.neverov
 */
@Test
public class BuildQueuePriorityOrderingTest {

  private static final File PLUGIN_CONFIG_FILE = new File(getTestDataDir(), PriorityClassManagerImpl.PRIORITY_CLASS_CONFIG_FILENAME);

  private TempFiles myTempFiles = new TempFiles();
  private Mockery myContext;
  private BuildQueueEx myQueue;
  private BuildQueuePriorityOrdering myStrategy;
  private ServerListener myListener;
  private List<SQueuedBuild> myCurrentQueueItems;
  private ProjectManager myProjectManager;
  private PriorityClassManagerImpl myPriorityClassManager;

  private int myQueuedBuildSeq = 0;
  private int myTimeIntervalSeq = 0;

  private SQueuedBuild myBt0;

  @SuppressWarnings("unchecked")
  @BeforeMethod(alwaysRun = true)
  public void setUp() throws IOException {
    new TestLogger().onSuiteStart();

    myContext = new Mockery(){{
      setImposteriser(ClassImposteriser.INSTANCE);
    }};
    final SBuildServer server = myContext.mock(SBuildServer.class);
    final ServerPaths serverPaths = Util.getServerPaths(myTempFiles.createTempDir());
    final EventDispatcher<BuildServerListener> eventDispatcher =
            (EventDispatcher<BuildServerListener>) myContext.mock(EventDispatcher.class);
    myQueue = myContext.mock(BuildQueueEx.class);
    myProjectManager = myContext.mock(ProjectManager.class);
    Loggers.SERVER.setLevel(Level.DEBUG);

    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt0");

    myBt0 = createQueuedBuild(id2buildType.get("bt0"), 60);
    myContext.checking(new Expectations() {{
      allowing(server).getQueue(); will(returnValue(myQueue));
      allowing(server).getFullServerVersion(); will(returnValue("1.0"));
      allowing(server).getProjectManager(); will(returnValue(myProjectManager));
      allowing(myQueue).setOrderingStrategy(with(any(BuildQueueOrderingStrategy.class)));
      allowing(myQueue).getItems(); will(returnValue(Collections.singletonList(myBt0)));
      allowing(eventDispatcher).addListener(with(any(BuildServerListener.class)));
    }});

    FileWatcherFactory fwf = new FileWatcherFactory(serverPaths);
    fwf.setCleanupManager(new Util.MockServerCleanupManager());
    myPriorityClassManager = new PriorityClassManagerImpl(server, serverPaths, eventDispatcher, fwf);
    myStrategy = new BuildQueuePriorityOrdering(server, myPriorityClassManager);
    myListener = new ServerListener(eventDispatcher, server, myStrategy, myPriorityClassManager);
    myListener.serverStartup();
    myCurrentQueueItems = new ArrayList<SQueuedBuild>();
  }


  @AfterMethod(alwaysRun = true)
  public void tearDown() throws InterruptedException {
    FileUtil.delete(PLUGIN_CONFIG_FILE);
    myTempFiles.cleanup();
  }


  public void test_without_priorities() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4");

    addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt1"), 60));
    assertOrder(myCurrentQueueItems, "bt1");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt2"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt3"), 60),
            createQueuedBuild(id2buildType.get("bt4"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2", "bt3", "bt4");
  }


  public void test_simple_priorities() throws IOException {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4");
    readConfig("build-queue-priorities-simple.xml");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt1"), 60));
    assertOrder(myCurrentQueueItems, "bt1");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt2"), 60));
    assertOrder(myCurrentQueueItems, "bt2", "bt1");
  }


  public void test_long_time_wait_item() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3");

    myPriorityClassManager.createPriorityClass("Zero", "", 0, Collections.singleton(id2buildType.get("bt1")));
    myPriorityClassManager.createPriorityClass("Four", "", 4, Collections.singleton(id2buildType.get("bt2")));
    myPriorityClassManager.createPriorityClass("Six", "", 6, Collections.singleton(id2buildType.get("bt3")));

    //add item that wait 5 its durations
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt1"), 1, 5));
    assertOrder(myCurrentQueueItems, "bt1");

    //add item with higher priority than bt1, but lower than 5
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt2"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2");

    //add item with priority > bt1.wait/bt1.duration
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt3", "bt1", "bt2");
  }


  public void test_items_removed() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4", "bt5");
    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt1"), 60),
            createQueuedBuild(id2buildType.get("bt2"), 60),
            createQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2", "bt3");

    SQueuedBuild bt1 = myCurrentQueueItems.remove(0);

    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt4"), 60),
            createQueuedBuild(id2buildType.get("bt5"), 60));
    assertOrder(myCurrentQueueItems, "bt2", "bt3", "bt4", "bt5");

    assertFalse(myStrategy.getCurrentPriorities().containsKey(bt1));
  }


  public void test_addBuilds_after_moveTop() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4", "bt5");

    myPriorityClassManager.createPriorityClass("Two", "", 2, new HashSet<SBuildType>(Arrays.asList(id2buildType.get("bt1"),
            id2buildType.get("bt2"), id2buildType.get("bt3"), id2buildType.get("bt4"))));

    myPriorityClassManager.createPriorityClass("Three", "", 3, Collections.singleton(id2buildType.get("bt5")));


    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt1"), 60),
            createQueuedBuild(id2buildType.get("bt2"), 60),
            createQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2", "bt3");

    //emulate move top:
    myCurrentQueueItems.add(0, myCurrentQueueItems.remove(2));
    assertOrder(myCurrentQueueItems, "bt3", "bt1", "bt2");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt4"), 60));
    assertOrder(myCurrentQueueItems, "bt3", "bt1", "bt2", "bt4");

    //some item with higher priority get higher place than item moved top
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt5"), 60));
    assertOrder(myCurrentQueueItems, "bt5", "bt3", "bt1", "bt2", "bt4");
  }


  public void test_addBuilds_after_moveBottom() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4", "bt5");

    myPriorityClassManager.createPriorityClass("Zero", "", 0, Collections.singleton(id2buildType.get("bt5")));
    myPriorityClassManager.createPriorityClass("Two", "", 2, new HashSet<SBuildType>(Arrays.asList(id2buildType.get("bt2"), id2buildType.get("bt3"))));
    myPriorityClassManager.createPriorityClass("Three", "", 3, new HashSet<SBuildType>(Arrays.asList(id2buildType.get("bt1"), id2buildType.get("bt4"))));


    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt1"), 60),
            createQueuedBuild(id2buildType.get("bt2"), 60),
            createQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2", "bt3");

    //emulate move bottom:
    myCurrentQueueItems.add(2, myCurrentQueueItems.remove(0));
    assertOrder(myCurrentQueueItems, "bt2", "bt3", "bt1");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt4"), 60));
    assertOrder(myCurrentQueueItems, "bt2", "bt4", "bt3", "bt1");

    //some item with lower priority get lower place than item moved bottom
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt5"), 60));
    assertOrder(myCurrentQueueItems, "bt2", "bt4", "bt3", "bt1", "bt5");
  }


  public void test_addBuilds_after_applyOrder() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4", "bt5", "bt6", "bt7", "bt8");

    myPriorityClassManager.createPriorityClass("Zero", "", 0, Collections.singleton(id2buildType.get("bt6")));

    myPriorityClassManager.createPriorityClass("Two", "", 2, new HashSet<SBuildType>(Arrays.asList(id2buildType.get("bt1"),
            id2buildType.get("bt2"), id2buildType.get("bt3"), id2buildType.get("bt4"), id2buildType.get("bt7"))));

    myPriorityClassManager.createPriorityClass("Three", "", 3, new HashSet<SBuildType>(Arrays.asList(id2buildType.get("bt5"), id2buildType.get("bt8"))));

    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt1"), 60),
            createQueuedBuild(id2buildType.get("bt2"), 60),
            createQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2", "bt3");

    //emulate apply order: swap bt1 and bt3
    Collections.swap(myCurrentQueueItems, 0, 2);
    assertOrder(myCurrentQueueItems, "bt3", "bt2", "bt1");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt4"), 60));
    assertOrder(myCurrentQueueItems, "bt3", "bt2", "bt1", "bt4");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt5"), 60));
    assertOrder(myCurrentQueueItems, "bt5", "bt3", "bt2", "bt1", "bt4");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt6"), 60));
    assertOrder(myCurrentQueueItems, "bt5", "bt3", "bt2", "bt1", "bt4", "bt6");

    //emulate another apply order: swap bt5 and bt6
    Collections.swap(myCurrentQueueItems, 0, 5);
    assertOrder(myCurrentQueueItems, "bt6", "bt3", "bt2", "bt1", "bt4", "bt5");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt7"), 60));
    //bt5 now get priority of bt6 (0), so bt7 get higher position than bt5, but lower than bt4 (because bt4
    //was in the queue for some time). It means that build with highest priority moved bottom does not prevent
    //other build from move top
    assertOrder(myCurrentQueueItems, "bt6", "bt3", "bt2", "bt1", "bt4", "bt7", "bt5");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt8"), 60));
    assertOrder(myCurrentQueueItems, "bt6", "bt8", "bt3", "bt2", "bt1", "bt4", "bt7", "bt5");
  }


  public void test_unknown_duration() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2");

    myPriorityClassManager.createPriorityClass("Zero", "", 0, Collections.singleton(id2buildType.get("bt1")));
    myPriorityClassManager.createPriorityClass("One", "", 1, Collections.singleton(id2buildType.get("bt2")));

    SQueuedBuild bt1 = createQueuedBuild(id2buildType.get("bt1"), null, 20 * 60);//item with unknown duration, wait in queue 2 defaultDurations
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, bt1);

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createQueuedBuild(id2buildType.get("bt2"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2"); // even if bt2 have higher priority it get lower position, because
    //bt1 with unknown priority already wait in queue 2 default duration intervals
  }


  public void test_personal_builds_priority() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3", "bt4", "bt5");

    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt1"), 60),
            createQueuedBuild(id2buildType.get("bt2"), 60),
            createPersonalQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt1", "bt2", "bt3");//by default - default and personal class have same priorities

    PriorityClass personalPriorityClass = myPriorityClassManager.getPersonalPriorityClass();
    PriorityClassImpl updatedPersonalClass = new PriorityClassImpl(myProjectManager, personalPriorityClass.getId(), personalPriorityClass.getName(),
            personalPriorityClass.getDescription(), 1, ((PriorityClassImpl)personalPriorityClass).getBuildTypeIds());
    myPriorityClassManager.savePriorityClass(updatedPersonalClass);

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createPersonalQueuedBuild(id2buildType.get("bt4"), 60));
    assertOrder(myCurrentQueueItems, "bt4", "bt1", "bt2", "bt3");

    updatedPersonalClass = new PriorityClassImpl(myProjectManager, personalPriorityClass.getId(), personalPriorityClass.getName(),
            personalPriorityClass.getDescription(), -1, ((PriorityClassImpl)personalPriorityClass).getBuildTypeIds());
    myPriorityClassManager.savePriorityClass(updatedPersonalClass);
    myCurrentQueueItems = addBuilds(myCurrentQueueItems, createPersonalQueuedBuild(id2buildType.get("bt5"), 60));
    assertOrder(myCurrentQueueItems, "bt4", "bt1", "bt2", "bt3", "bt5");
  }


  /**
   * When buildQueue restored from DB second argument in addBuilds() contains
   * some unknown items, check that there is no NPE
   */
  public void test_buildQueue_restored_from_DB() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt1", "bt2", "bt3");
    myCurrentQueueItems.add(myBt0);
    myCurrentQueueItems = addBuilds(myCurrentQueueItems,
            createQueuedBuild(id2buildType.get("bt1"), 60),
            createQueuedBuild(id2buildType.get("bt2"), 60),
            createQueuedBuild(id2buildType.get("bt3"), 60));
    assertOrder(myCurrentQueueItems, "bt0", "bt1", "bt2", "bt3");
  }


  /**
   * Since QueuedBuildImpl does not implement equals() and hashCode(), ordering strategy should not rely on these methods.
   * Otherwise NPE could be thrown because we cannot find a weight for the queued build.
   */
  public void test_TW_13883() {
    Map<String, SBuildType> id2buildType = prepareBuildTypes(myContext, myProjectManager, "bt2", "bt3");
    final SQueuedBuild qb1 = createQueuedBuild(id2buildType.get("bt2"), 60);

    myCurrentQueueItems = addBuilds(myCurrentQueueItems, qb1);
    assertEquals(1, myCurrentQueueItems.size());

    final SQueuedBuild qb1copy = createQueuedBuild(id2buildType.get("bt2"), createBuildEstimates(createTimeInterval(60)), new Date(), false, qb1.getItemId());
    assertEquals(qb1.getItemId(), qb1copy.getItemId());
    List<SQueuedBuild> changedCurrentItems = new ArrayList<SQueuedBuild>();
    changedCurrentItems.add(qb1copy);
    myCurrentQueueItems = addBuilds(changedCurrentItems, createQueuedBuild(id2buildType.get("bt3"), 60));
    assertFalse(myCurrentQueueItems.isEmpty()); //it is empty in the case of errors
  }


  private void readConfig(String configPath) throws IOException {
    File testConfig = new File(getTestDataDir(), configPath);
    FileUtil.copy(testConfig, PLUGIN_CONFIG_FILE);
    myPriorityClassManager.loadPriorityClasses();
  }

  private void assertOrder(List<SQueuedBuild> items, String... expectedBuildTypeIds) {
    List<String> actual = new ArrayList<String>();
    for (SQueuedBuild qb: items) {
      actual.add(qb.getBuildTypeId());
    }
    assertEquals(Arrays.asList(expectedBuildTypeIds), actual);
  }

  private List<SQueuedBuild> addBuilds(List<SQueuedBuild> currentQueueItems, SQueuedBuild... builds) {
    return myStrategy.addBuilds(Arrays.asList(builds), currentQueueItems);
  }

  private SQueuedBuild createPersonalQueuedBuild(final SBuildType buildType, final Integer durationSec, long... queuedSecondsAgo) {
    final Date whenQueued;
    if (queuedSecondsAgo.length > 0) {
      whenQueued = new Date(new Date().getTime() - queuedSecondsAgo[0] * 1000);
    } else {
      whenQueued = new Date();
    }
    return createQueuedBuild(buildType, createBuildEstimates(createTimeInterval(durationSec)), whenQueued, true);
  }

  private SQueuedBuild createQueuedBuild(SBuildType buildType, final Integer durationSec, long... queuedSecondsAgo) {
    final Date whenQueued;
    if (queuedSecondsAgo.length > 0) {
      whenQueued = new Date(new Date().getTime() - queuedSecondsAgo[0] * 1000);
    } else {
      whenQueued = new Date();
    }
    return createQueuedBuild(buildType, createBuildEstimates(createTimeInterval(durationSec)), whenQueued, false);
  }

  private SQueuedBuild createQueuedBuild(final SBuildType buildType,
                                         final BuildEstimates buildEstimates,
                                         final Date whenQueued,
                                         final boolean personal,
                                         final String... itemId) {
    final SQueuedBuild qb = myContext.mock(SQueuedBuild.class, "SQueuedBuild" + myQueuedBuildSeq++);
    myContext.checking(new Expectations() {{
      allowing(qb).getBuildTypeId(); will(returnValue(buildType.getBuildTypeId()));
      allowing(qb).getBuildEstimates(); will(returnValue(buildEstimates));
      allowing(qb).getWhenQueued(); will(returnValue(whenQueued));
      allowing(qb).isPersonal(); will(returnValue(personal));
      allowing(qb).getBuildType(); will(returnValue(buildType));
      String queuedBuildItemId = (itemId.length > 0) ? itemId[0] : String.valueOf(myQueuedBuildSeq);
      allowing(qb).getItemId(); will(returnValue(String.valueOf(queuedBuildItemId)));
      allowing(myQueue).findQueued(queuedBuildItemId); will(returnValue(qb));
    }});
    return qb;
  }

  private BuildEstimates createBuildEstimates(final TimeInterval timeInterval) {
    return new BuildEstimates(timeInterval, null, null, false);
  }

  private TimeInterval createTimeInterval(final Integer duration) {
    final TimeInterval interval = myContext.mock(TimeInterval.class, "TimeInterval" + myTimeIntervalSeq++);
    final Long durationToReturn = duration != null ? duration.longValue() : null;
    myContext.checking(new Expectations(){{
      allowing(interval).getDurationSeconds(); will(returnValue(durationToReturn));
    }});
    return interval;
  }
}
