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
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import jetbrains.buildServer.configuration.ChangeListener;
import jetbrains.buildServer.configuration.FileWatcher;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.FileWatcherFactory;
import jetbrains.buildServer.serverSide.priority.exceptions.DuplicatePriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassDescriptionException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.PriorityClassException;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.util.Assert;

import static java.util.Arrays.asList;

/**
 * PriorityClassManager implementation.
 * Stores priorityClass in config file.
 */
public final class PriorityClassManagerImpl extends BuildServerAdapter implements PriorityClassManager {

  public static final String PRIORITY_CLASS_CONFIG_FILENAME = "build-queue-priorities.xml";
  private final static String PRIORITY_CLASS_ROOT_ELEMENT = "priority-classes";
  private final static String PRIORITY_CLASS_ELEMENT = "priority-class";
  private final static String BUILD_TYPE_ELEMENT = "build-type";
  private final static String BUILD_TYPE_ID_ATTRIBUTE = "id";
  private final static String ID_ATTRIBUTE = "id";
  private final static String NAME_ATTRIBUTE = "name";
  private final static String DESCRIPTION_ATTRIBUTE = "description";
  private final static String PRIORITY_ATTRIBUTE = "priority";
  private final static String DEFAULT_PRIORITY_CLASS_ID = "DEFAULT";
  private final static String PERSONAL_PRIORITY_CLASS_ID = "PERSONAL";

  private final PriorityClassImpl myPersonalPriorityClass;

  private final File myConfigFile;
  private final Logger myLogger = Logger.getLogger(PriorityClassManagerImpl.class.getName());

  private final Map<String, PriorityClassImpl> myPriorityClasses = new HashMap<String, PriorityClassImpl>();
  private final Map<String, String> myBuildTypePriorityClasses = new HashMap<String, String>();//external id -> priorityClass id
  private final AtomicInteger myPriorityClassIdSequence = new AtomicInteger(1);
  private final SBuildServer myServer;
  private FileWatcherFactory myFileWatcherFactory;
  private FileWatcher myConfigFileWatcher;
  private int myUpdateConfigInterval;
  private EventDispatcher<BuildServerListener> myServerDispatcher;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  public PriorityClassManagerImpl(@NotNull final SBuildServer server,
                                  @NotNull final ServerPaths serverPaths,
                                  @NotNull final EventDispatcher<BuildServerListener> serverDispatcher,
                                  @NotNull final FileWatcherFactory fileWatcherFactory) {
    myServer = server;
    myConfigFile = new File(serverPaths.getConfigDir(), PRIORITY_CLASS_CONFIG_FILENAME);
    myServerDispatcher = serverDispatcher;
    myFileWatcherFactory = fileWatcherFactory;
    myPersonalPriorityClass = new PersonalPriorityClass(0);
  }

  public void setUpdateConfigInterval(int seconds) {
    myUpdateConfigInterval = seconds;
  }

  public void init() {
    Assert.notNull(myServerDispatcher);
    Assert.notNull(myFileWatcherFactory);
    Assert.notNull(myServer);

    loadPriorityClasses();

    startFileWatching();
  }

  @Override
  public void buildTypeExternalIdChanged(@NotNull final SBuildType buildType,
                                         @NotNull final String oldExternalId,
                                         @NotNull final String newExternalId) {
    myLock.writeLock().lock();
    try {
      String priorityClassId = myBuildTypePriorityClasses.remove(oldExternalId);
      if (priorityClassId != null) {
        myBuildTypePriorityClasses.put(newExternalId, priorityClassId);
        PriorityClassImpl pc = myPriorityClasses.get(priorityClassId);
        PriorityClassImpl updated = (PriorityClassImpl) pc.updateExternalId(oldExternalId, newExternalId);
        myPriorityClasses.put(priorityClassId, updated);
      }
    } finally {
      myLock.writeLock().unlock();
    }
  }

  @NotNull
  public List<PriorityClass> getAllPriorityClasses() {
    myLock.readLock().lock();
    try {
      return new ArrayList<PriorityClass>(myPriorityClasses.values());
    } finally {
      myLock.readLock().unlock();
    }
  }

  @Nullable
  public PriorityClass findPriorityClassById(@NotNull final String priorityClassId) {
    myLock.readLock().lock();
    try {
      return myPriorityClasses.get(priorityClassId);
    } finally {
      myLock.readLock().unlock();
    }
  }

  @Nullable
  public PriorityClass findPriorityClassByName(@NotNull final String priorityClassName) {
    myLock.readLock().lock();
    try {
      for (PriorityClassImpl priorityClass : myPriorityClasses.values()) {
        if (priorityClass.getName().equals(priorityClassName)) {
          return priorityClass;
        }
      }
    } finally {
       myLock.readLock().unlock();
    }
    return null;
  }

  @NotNull
  public PriorityClass getBuildTypePriorityClass(@NotNull SBuildType buildType) {
    myLock.readLock().lock();
    try {
      PriorityClass priorityClass = findBuildTypePriorityClass(buildType.getExternalId());
      if (priorityClass != null) {
        return priorityClass;
      } else {
        return getDefaultPriorityClass();
      }
    } finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  public PriorityClass createPriorityClass(@NotNull final String name, @NotNull final String description, int priority)
          throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException, DuplicatePriorityClassNameException {
    return createPriorityClass(name, description, priority, new HashSet<SBuildType>());
  }

  @NotNull
  public PriorityClass createPriorityClass(@NotNull final String name, @NotNull final String description, int priority,
                                           @NotNull Set<SBuildType> buildTypes)
          throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException, DuplicatePriorityClassNameException {
    String id = "pc" + myPriorityClassIdSequence.incrementAndGet();

    final PriorityClassImpl priorityClass;
    myLock.writeLock().lock();
    try {
      PriorityClass sameNamePriorityClass = findPriorityClassByName(name);
      if (sameNamePriorityClass != null) {
        throw new DuplicatePriorityClassNameException("The priority class name '" + name + "' already exists");
      }
      priorityClass = new PriorityClassImpl(myServer.getProjectManager(), id, name, description, priority, getBuildTypeIds(buildTypes));
      myPriorityClasses.put(priorityClass.getId(), priorityClass);
      for (SBuildType bt : priorityClass.getBuildTypes()) {
        myBuildTypePriorityClasses.put(bt.getExternalId(), priorityClass.getId());
      }
    } finally {
      myLock.writeLock().unlock();
    }
    onPriorityClassesChanged();
    return priorityClass;
  }

  public void savePriorityClass(@NotNull final PriorityClass priorityClass) throws DuplicatePriorityClassNameException {
    if (priorityClass.getId().equals(DEFAULT_PRIORITY_CLASS_ID)) return;
    
    myLock.writeLock().lock();
    try {
      if (priorityClass.getId().equals(PERSONAL_PRIORITY_CLASS_ID)) {
        myPriorityClasses.put(PERSONAL_PRIORITY_CLASS_ID, new PersonalPriorityClass(priorityClass.getPriority()));
      } else {
        PriorityClass sameNamePriorityClass = findPriorityClassByName(priorityClass.getName());
        if (sameNamePriorityClass != null && !priorityClass.equals(sameNamePriorityClass)) {
          throw new DuplicatePriorityClassNameException("The priority class name '" + priorityClass.getName() + "' already exists");
        }
        PriorityClassImpl oldPc = myPriorityClasses.get(priorityClass.getId());
        if (oldPc != null) {
          Set<String> oldBuildTypeIds = oldPc.getExternalIds();
          oldBuildTypeIds.removeAll(((PriorityClassImpl) priorityClass).getExternalIds());
          for (String btId : oldBuildTypeIds) {
            myBuildTypePriorityClasses.remove(btId);
          }
        }
        for (String btId : ((PriorityClassImpl)priorityClass).getExternalIds()) {
          PriorityClass oldPriorityClass = findBuildTypePriorityClass(btId);
          if (oldPriorityClass != null) {
            Set<String> builtTypeIds = ((PriorityClassImpl) oldPriorityClass).getExternalIds();
            builtTypeIds.remove(btId);
            PriorityClassImpl updatedOldPriorityClass = (PriorityClassImpl) oldPriorityClass.removeBuildTypes(asList(btId));
            myPriorityClasses.put(updatedOldPriorityClass.getId(), updatedOldPriorityClass);
          }
          myBuildTypePriorityClasses.put(btId, priorityClass.getId());
        }
        myPriorityClasses.put(priorityClass.getId(), (PriorityClassImpl) priorityClass);
      }
    } finally {
      myLock.writeLock().unlock();
    }
    onPriorityClassesChanged();
  }

  public void deletePriorityClass(@NotNull final String priorityClassId) {
    if (!priorityClassId.equals(DEFAULT_PRIORITY_CLASS_ID) && !priorityClassId.equals(PERSONAL_PRIORITY_CLASS_ID)) {
      myLock.writeLock().lock();
      try {
        PriorityClass removed = myPriorityClasses.remove(priorityClassId);
        if (removed != null) {
          for (SBuildType bt : removed.getBuildTypes()) {
            myBuildTypePriorityClasses.remove(bt.getExternalId());
          }          
        }
      } finally {
        myLock.writeLock().unlock();
      }
      onPriorityClassesChanged();
    }
  }

  public boolean isDefaultPriorityClass(@NotNull final PriorityClass priorityClass) {
    return priorityClass.getId().equals(DEFAULT_PRIORITY_CLASS_ID);
  }

  public boolean isPersonalPriorityClass(@NotNull PriorityClass priorityClass) {
    return priorityClass.getId().equals(PERSONAL_PRIORITY_CLASS_ID);
  }

  @NotNull
  private List<SBuildType> getAllUnassignedBuildTypes() {
    List<SBuildType> allBuildTypes = myServer.getProjectManager().getAllBuildTypes();
    myLock.readLock().lock();
    try {
      for (PriorityClassImpl priorityClass : myPriorityClasses.values()) {
        if (!isDefaultPriorityClass(priorityClass)) {
          allBuildTypes.removeAll(priorityClass.getBuildTypes());
        }
      }
    } finally {
      myLock.readLock().unlock();
    }
    return allBuildTypes;
  }

  @NotNull
  public PriorityClass getDefaultPriorityClass() {
    return myPriorityClasses.get(DEFAULT_PRIORITY_CLASS_ID);
  }

  @NotNull
  public PriorityClass getPersonalPriorityClass() {
    return myPriorityClasses.get(PERSONAL_PRIORITY_CLASS_ID);
  }

  private void onPriorityClassesChanged() {
    savePriorityClasses();
  }

  private void startFileWatching() {
    myConfigFileWatcher = myFileWatcherFactory.createSingleFilesWatcher(myConfigFile, myUpdateConfigInterval);
    myConfigFileWatcher.registerListener(new ChangeListener() {
      public void changeOccured(final String requestor) {
        if (!myConfigFile.isFile()) {
          myLogger.warn("Priority classes configuration file deleted: " + myConfigFile.getAbsolutePath());
          return;
        }
        loadPriorityClasses();
      }
    });
    myServerDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void serverShutdown() {
        myConfigFileWatcher.stop();
      }

      @Override
      public void beforeBuildTypeDeleted(@NotNull final String buildTypeId) {
        savePriorityClasses();
      }
    });
    myConfigFileWatcher.start();
  }


  /**
   * Load priorityClass definitions from config file.
   * This method is package private for tests
   */
  void loadPriorityClasses() {
    myLogger.info("Loading priority classes from the configuration file: " + myConfigFile.getAbsolutePath());
    final Map<String, PriorityClassImpl> priorityClassMap = new HashMap<String, PriorityClassImpl>();
    int maxPriorityClassId = 0;
    Pattern idPattern = Pattern.compile("pc\\d+"); 
    try {
      SAXBuilder builder = new SAXBuilder(false);
      if (myConfigFile.exists()) {
        Document doc = builder.build(myConfigFile);
        Element rootElement = doc.getRootElement();

        List<Element> priorityClassElements = new PriorityClassElementVisitor(rootElement).getPriorityClassElements();
        for (Element priorityClassElem : priorityClassElements) {
          final String id = priorityClassElem.getAttributeValue(ID_ATTRIBUTE);
          if (id.equals(DEFAULT_PRIORITY_CLASS_ID)) {
            myLogger.warn("Priority Class with id " + DEFAULT_PRIORITY_CLASS_ID +
                    " cannot be reconfigured via the " + myConfigFile.getName() + ", please remove it from the " + myConfigFile.getName());
          } else if (id.equals(PERSONAL_PRIORITY_CLASS_ID)) {
            int priority = parsePriorityString(priorityClassElem.getAttributeValue(PRIORITY_ATTRIBUTE), id);
            PriorityClassImpl personalPriorityClass = new PersonalPriorityClass(priority);
            priorityClassMap.put(personalPriorityClass.getId(), personalPriorityClass);
            //TODO: if name, description or buidltypes are changed - WARN
          } else {
            if (idPattern.matcher(id).matches()) {
              maxPriorityClassId = Integer.parseInt(id.substring(2));
            }

            if (priorityClassMap.containsKey(id)) {
              throw new RuntimeException("Failed to load " + myConfigFile.getName() + ". Duplicate priority class identificator found: " + id);
            }

            int priority = parsePriorityString(priorityClassElem.getAttributeValue(PRIORITY_ATTRIBUTE), id);
            Set<String> externalIds = new BuildTypeElementVisitor(priorityClassElem).getBuildTypeIds();
            PriorityClassImpl priorityClass = new PriorityClassImpl(myServer.getProjectManager(), id, priorityClassElem.getAttributeValue(NAME_ATTRIBUTE),
                    priorityClassElem.getAttributeValue(DESCRIPTION_ATTRIBUTE), priority,
                    externalIds);
            priorityClassMap.put(id, priorityClass);            
          }
        }
      }
    } catch (Throwable e) {
      myLogger.warn("Exception occured while reading priority classes from the file: " +
              myConfigFile.getAbsolutePath() + ", error message: " + e.toString());
      myLogger.debug(e.toString(), e);
      myLock.writeLock().lock();
      try {
        for (PriorityClassImpl predefinedPriorityClass : getPredefinedPriorityClasses()) {
          if (!myPriorityClasses.containsKey(predefinedPriorityClass.getId())) {
            myPriorityClasses.put(predefinedPriorityClass.getId(), predefinedPriorityClass);
          }
        }
      } finally {
        myLock.writeLock().unlock();
      }
      return;
    }

    myLock.writeLock().lock();
    try {
      myPriorityClasses.clear();
      for (PriorityClassImpl priorityClass : priorityClassMap.values()) {
        myPriorityClasses.put(priorityClass.getId(), priorityClass);
        for (String btId : priorityClass.getExternalIds()) {
          myBuildTypePriorityClasses.put(btId, priorityClass.getId());
        }
      }
      for (PriorityClassImpl predefinedPriorityClass : getPredefinedPriorityClasses()) {
        if (!myPriorityClasses.containsKey(predefinedPriorityClass.getId())) {
          myPriorityClasses.put(predefinedPriorityClass.getId(), predefinedPriorityClass);
        }
      }
      myPriorityClassIdSequence.set(maxPriorityClassId);
    } finally {
      myLock.writeLock().unlock();
    }
  }


  /**
   * Parse priority string.
   * @param priorityString priority string
   * @param id id of priorityClass, used to write log if priority is invalid
   * @return parsed priority
   */
  private int parsePriorityString(final String priorityString, String id) {
    int priority = 0;
    if (priorityString != null) {
      try {
        priority = Integer.parseInt(priorityString);
      } catch (NumberFormatException e) {
        myLogger.warn("Invalid priority specified for priority class " + id + ": " + priorityString);
      }
    }
    return priority;
  }

  private List<PriorityClassImpl> getPredefinedPriorityClasses() {
    List<PriorityClassImpl> result = new ArrayList<PriorityClassImpl>();
    result.add(new DefaultPriorityClass());
    result.add(myPersonalPriorityClass);
    return result;
  }

  private void savePriorityClasses() {
    myConfigFileWatcher.runActionWithDisabledObserver(new Runnable() {
      public void run() {
        try {
          Document document = new Document();
          final Element rootElement = new Element(PRIORITY_CLASS_ROOT_ELEMENT);
          document.setRootElement(rootElement);

          myLock.readLock().lock();
          try {
            for (PriorityClassImpl priorityClass : myPriorityClasses.values()) {
              if (isDefaultPriorityClass(priorityClass)) continue;

              Element priorityClassElement = new Element(PRIORITY_CLASS_ELEMENT);
              priorityClassElement.setAttribute(ID_ATTRIBUTE, priorityClass.getId());
              priorityClassElement.setAttribute(NAME_ATTRIBUTE, priorityClass.getName());
              priorityClassElement.setAttribute(PRIORITY_ATTRIBUTE, String.valueOf(priorityClass.getPriority()));
              priorityClassElement.setAttribute(DESCRIPTION_ATTRIBUTE, priorityClass.getDescription());

              for (String btId : priorityClass.getExternalIds()) {
                final Element buildTypeElement = new Element(BUILD_TYPE_ELEMENT);
                buildTypeElement.setAttribute(BUILD_TYPE_ID_ATTRIBUTE, btId);
                priorityClassElement.addContent(buildTypeElement);
              }
              rootElement.addContent(priorityClassElement);
            }
          } finally {
            myLock.readLock().unlock();
          }

          FileUtil.saveDocument(document, myConfigFile);
        } catch (Exception e) {
          myLogger.error("Error saving priority classes: "+ e.toString());
          myLogger.debug(e.getMessage(), e);
        }
      }
    });

  }

  private final class DefaultPriorityClass extends PriorityClassImpl {
    private DefaultPriorityClass() throws PriorityClassException {
      super(myServer.getProjectManager(), DEFAULT_PRIORITY_CLASS_ID, "Default", "Contains all build configurations not included into other priority classes", 0,
              Collections.<String>emptySet());
    }
    @NotNull
    public List<SBuildType> getBuildTypes() {
      return getAllUnassignedBuildTypes();
    }
    public boolean isDefaultPriorityClass() {
      return true;
    }
  }

  private PriorityClass findBuildTypePriorityClass(String externalId) {
    String priorityClassId = myBuildTypePriorityClasses.get(externalId);
    if (priorityClassId != null) {
      return myPriorityClasses.get(priorityClassId);      
    } else {
      return null;
    }
  }

  private final class PersonalPriorityClass extends PriorityClassImpl {
    private PersonalPriorityClass(int priority) throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException {
      super(myServer.getProjectManager(), PERSONAL_PRIORITY_CLASS_ID, "Personal", "Contains all personal builds", priority, Collections.<String>emptySet());
    }
    public boolean isPersonal() {
      return true;
    }
  }

  private static class PriorityClassElementVisitor extends JDOMElementVisitor {
    private List<Element> myPriorityClassElements;
    private PriorityClassElementVisitor(final Element e) {
      super(e);
    }

    @Override
    public boolean processElement(final Element e) {
      if (e.getName().equals(PRIORITY_CLASS_ELEMENT)) {
        if (myPriorityClassElements == null) {
          myPriorityClassElements = new ArrayList<Element>();
        }
        myPriorityClassElements.add(e);
        return false;
      }
      return true;
    }

    public List<Element> getPriorityClassElements() {
      if (myPriorityClassElements != null) {
        return myPriorityClassElements;
      } else {
        return new ArrayList<Element>();
      }
    }
  }

  private class BuildTypeElementVisitor extends JDOMElementVisitor {
    private Set<String> myBuildTypeIds; // do not init it here, because processElement is invoked from constructor
    private BuildTypeElementVisitor(Element e) {
      super(e);
    }

    @Override
    public boolean processElement(Element e) {
      if (e.getName().equals(BUILD_TYPE_ELEMENT)) {
        String buildTypeId = e.getAttributeValue(BUILD_TYPE_ID_ATTRIBUTE);
        if (buildTypeId != null && !"".equals(buildTypeId)) {
          if (myBuildTypeIds == null) {
            myBuildTypeIds = new HashSet<String>();
          }
          myBuildTypeIds.add(buildTypeId);
        }
        return false;
      }
      return true;
    }

    Set<String> getBuildTypeIds() {
      if (myBuildTypeIds != null) {
        return myBuildTypeIds;
      } else {
        return new HashSet<String>();
      }
    }
  }

  /**
   * Copy of jetbrains.buildServer.util.JDOMElementVisitor
   */
  private static abstract class JDOMElementVisitor {
    protected JDOMElementVisitor(Element e) {
      visitElement(e);
    }

    private void visitElement(Element e) {
      if (!processElement(e)) return;
      for (Object o : e.getChildren()) {
        visitElement((Element)o);
      }
    }

    /**
     * @return false if child elements for given element should not be processed
     * @param e element to process
     * */
    public abstract boolean processElement(Element e);
  }

  private static Collection<String> getBuildTypeIds(Collection<SBuildType> buildTypes) {
    return CollectionsUtil.convertCollection(buildTypes, new Converter<String, SBuildType>() {
      public String createFrom(@NotNull final SBuildType source) {
        return source.getExternalId();
      }
    });
  }
}
