/*
 * Copyright 2000-2022 JetBrains s.r.o.
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Pattern;
import jetbrains.buildServer.configuration.FileWatcher;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.impl.FileWatcherFactory;
import jetbrains.buildServer.serverSide.impl.persisting.SettingsPersister;
import jetbrains.buildServer.serverSide.priority.exceptions.DuplicatePriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassDescriptionException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.PriorityClassException;
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.util.FileUtil;
import org.apache.log4j.Logger;
import org.jdom.Content;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Arrays.asList;

/**
 * PriorityClassManager implementation.
 * Stores priorityClass in config file.
 */
public final class PriorityClassManagerImpl extends BuildServerAdapter implements PriorityClassManager {

  private static final Logger myLogger = Logger.getLogger(PriorityClassManagerImpl.class.getName());

  static final String PRIORITY_CLASS_CONFIG_FILENAME = "build-queue-priorities.xml";
  private static final String PRIORITY_CLASS_ROOT_ELEMENT = "priority-classes";
  private static final String PRIORITY_CLASS_ELEMENT = "priority-class";
  private static final String BUILD_TYPE_ELEMENT = "build-type";
  private static final String BUILD_TYPE_ID_ATTRIBUTE = "id";
  private static final String ID_ATTRIBUTE = "id";
  private static final String NAME_ATTRIBUTE = "name";
  private static final String DESCRIPTION_ATTRIBUTE = "description";
  private static final String PRIORITY_ATTRIBUTE = "priority";
  private static final String DEFAULT_PRIORITY_CLASS_ID = "DEFAULT";
  private static final String PERSONAL_PRIORITY_CLASS_ID = "PERSONAL";

  private final PriorityClassImpl myPersonalPriorityClass;

  private final File myConfigFile;

  private final Map<String, PriorityClassImpl> myPriorityClasses = new HashMap<>();
  private final Map<String, String> myBuildTypePriorityClasses = new HashMap<>();//external id -> priorityClass id
  private final SBuildServer myServer;
  private final FileWatcherFactory myFileWatcherFactory;
  private final SettingsPersister mySettingsPersister;
  private FileWatcher myConfigFileWatcher;
  private int myUpdateConfigInterval;
  private final EventDispatcher<BuildServerListener> myServerDispatcher;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();

  private final Pattern myIdPattern = Pattern.compile("pc\\d+");

  public PriorityClassManagerImpl(@NotNull final SBuildServer server,
                                  @NotNull final ServerPaths serverPaths,
                                  @NotNull final EventDispatcher<BuildServerListener> serverDispatcher,
                                  @NotNull final FileWatcherFactory fileWatcherFactory,
                                  @NotNull final SettingsPersister settingsPersister) {
    myServer = server;
    myConfigFile = new File(serverPaths.getConfigDir(), PRIORITY_CLASS_CONFIG_FILENAME);
    myServerDispatcher = serverDispatcher;
    myFileWatcherFactory = fileWatcherFactory;
    mySettingsPersister = settingsPersister;
    myPersonalPriorityClass = new PersonalPriorityClass(0);
  }

  public void setUpdateConfigInterval(int seconds) {
    myUpdateConfigInterval = seconds;
  }

  public void init() {
    loadPriorityClasses();

    startFileWatching();
    myServerDispatcher.addListener(this);
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
        PriorityClassImpl updated = (PriorityClassImpl)pc.updateExternalId(oldExternalId, newExternalId);
        myPriorityClasses.put(priorityClassId, updated);
        savePriorityClasses();
      }
    } finally {
      myLock.writeLock().unlock();
    }
  }

  @Override
  @NotNull
  public List<PriorityClass> getAllPriorityClasses() {
    myLock.readLock().lock();
    try {
      return new ArrayList<>(myPriorityClasses.values());
    } finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  @Nullable
  public PriorityClass findPriorityClassById(@NotNull final String priorityClassId) {
    myLock.readLock().lock();
    try {
      return myPriorityClasses.get(priorityClassId);
    } finally {
      myLock.readLock().unlock();
    }
  }

  @Override
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

  @Override
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

  @Override
  @NotNull
  public PriorityClass createPriorityClass(@NotNull final String name, @NotNull final String description, int priority)
    throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException, DuplicatePriorityClassNameException {
    return createPriorityClass(name, description, priority, new HashSet<>());
  }

  @Override
  @NotNull
  public PriorityClass createPriorityClass(@NotNull final String name, @NotNull final String description, int priority,
                                           @NotNull Set<SBuildType> buildTypes)
    throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException, DuplicatePriorityClassNameException {
    final PriorityClassImpl priorityClass;
    myLock.writeLock().lock();
    try {
      PriorityClass sameNamePriorityClass = findPriorityClassByName(name);
      if (sameNamePriorityClass != null) {
        throw new DuplicatePriorityClassNameException("The priority class name '" + name + "' already exists");
      }
      String id = "pc" + getNextSequenceId();
      priorityClass = new PriorityClassImpl(myServer.getProjectManager(), id, name, description, priority, getBuildTypeIds(buildTypes));
      myPriorityClasses.put(priorityClass.getId(), priorityClass);
      for (SBuildType bt : priorityClass.getBuildTypes()) {
        myBuildTypePriorityClasses.put(bt.getExternalId(), priorityClass.getId());
      }
    } finally {
      myLock.writeLock().unlock();
    }
    savePriorityClasses();
    return priorityClass;
  }

  private int getNextSequenceId() {
    return myPriorityClasses.values().stream()
                            .map(PriorityClassImpl::getId)
                            .filter(id -> myIdPattern.matcher(id).matches())
                            .map(id -> Integer.parseInt(id.substring(2)))
                            .max(Comparator.naturalOrder())
                            .orElse(0) + 1;
  }

  @Override
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
          oldBuildTypeIds.removeAll(((PriorityClassImpl)priorityClass).getExternalIds());
          for (String btId : oldBuildTypeIds) {
            myBuildTypePriorityClasses.remove(btId);
          }
        }
        for (String btId : ((PriorityClassImpl)priorityClass).getExternalIds()) {
          PriorityClass oldPriorityClass = findBuildTypePriorityClass(btId);
          if (oldPriorityClass != null) {
            SBuildType buildType = myServer.getProjectManager().findBuildTypeByExternalId(btId);
            if (buildType != null) {
              PriorityClassImpl updatedOldPriorityClass = (PriorityClassImpl)oldPriorityClass.removeBuildTypes(asList(buildType.getBuildTypeId()));
              myPriorityClasses.put(updatedOldPriorityClass.getId(), updatedOldPriorityClass);
            }
          }
          myBuildTypePriorityClasses.put(btId, priorityClass.getId());
        }
        myPriorityClasses.put(priorityClass.getId(), (PriorityClassImpl)priorityClass);
      }
    } finally {
      myLock.writeLock().unlock();
    }
    savePriorityClasses();
  }

  @Override
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
      savePriorityClasses();
    }
  }

  @Override
  public boolean isDefaultPriorityClass(@NotNull final PriorityClass priorityClass) {
    return priorityClass.getId().equals(DEFAULT_PRIORITY_CLASS_ID);
  }

  @Override
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

  @Override
  @NotNull
  public PriorityClass getDefaultPriorityClass() {
    return myPriorityClasses.get(DEFAULT_PRIORITY_CLASS_ID);
  }

  @Override
  @NotNull
  public PriorityClass getPersonalPriorityClass() {
    return myPriorityClasses.get(PERSONAL_PRIORITY_CLASS_ID);
  }

  private void startFileWatching() {
    myConfigFileWatcher = myFileWatcherFactory.createFileWatcher(myConfigFile, myUpdateConfigInterval);
    myConfigFileWatcher.registerListener(requestor -> {
      if (!myConfigFile.isFile()) {
        myLogger.warn("Priority classes configuration file deleted: " + myConfigFile.getAbsolutePath());
        return;
      }
      loadPriorityClasses();
    });
    myServerDispatcher.addListener(new BuildServerAdapter() {
      @Override
      public void serverShutdown() {
        myConfigFileWatcher.stop();
      }

      @Override
      public void beforeBuildTypesDeleted() {
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
    final Map<String, PriorityClassImpl> priorityClassMap = new HashMap<>();
    Pattern idPattern = Pattern.compile("pc\\d+");
    try {
      if (myConfigFile.exists()) {
        Document doc = FileUtil.parseDocument(myConfigFile, false).getDocument();
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
    } finally {
      myLock.writeLock().unlock();
    }
  }


  /**
   * Parse priority string.
   *
   * @param priorityString priority string
   * @param id             id of priorityClass, used to write log if priority is invalid
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
    List<PriorityClassImpl> result = new ArrayList<>();
    result.add(new DefaultPriorityClass());
    result.add(myPersonalPriorityClass);
    return result;
  }

  private void savePriorityClasses() {
    Document document = getDocument();
    try {
      mySettingsPersister.scheduleSaveDocument("Save Priority classes", myConfigFileWatcher, document);
    } catch (IOException e) {
      myLogger.error("Error saving priority classes: " + e);
      myLogger.debug(e.getMessage(), e);
    }
  }

  @NotNull
  Document getDocument() {
    Document document = new Document();
    Element rootElement = new Element(PRIORITY_CLASS_ROOT_ELEMENT);
    document.setRootElement(rootElement);

    myLock.readLock().lock();
    try {
      myPriorityClasses.values().stream()
                       .sorted(Comparator.comparing(pc -> myIdPattern.matcher(pc.getId()).matches() ? Integer.parseInt(pc.getId().substring(2)) : 0))
                       .forEach(priorityClass -> {
        if (isDefaultPriorityClass(priorityClass)) return;

        Element priorityClassElement = new Element(PRIORITY_CLASS_ELEMENT);
        priorityClassElement.setAttribute(ID_ATTRIBUTE, priorityClass.getId());
        priorityClassElement.setAttribute(NAME_ATTRIBUTE, priorityClass.getName());
        priorityClassElement.setAttribute(PRIORITY_ATTRIBUTE, String.valueOf(priorityClass.getPriority()));
        priorityClassElement.setAttribute(DESCRIPTION_ATTRIBUTE, priorityClass.getDescription());

        for (String btId : priorityClass.getExternalIds()) {
          final Element buildTypeElement = new Element(BUILD_TYPE_ELEMENT);
          buildTypeElement.setAttribute(BUILD_TYPE_ID_ATTRIBUTE, btId);
          priorityClassElement.addContent((Content)buildTypeElement);
        }
        rootElement.addContent((Content)priorityClassElement);
      });
    } finally {
      myLock.readLock().unlock();
    }
    return document;
  }

  private final class DefaultPriorityClass extends PriorityClassImpl {
    private DefaultPriorityClass() throws PriorityClassException {
      super(myServer.getProjectManager(), DEFAULT_PRIORITY_CLASS_ID, "Default", "Contains all build configurations not included into other priority classes", 0,
        Collections.emptySet());
    }

    @Override
    @NotNull
    public List<SBuildType> getBuildTypes() {
      return getAllUnassignedBuildTypes();
    }

    @Override
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
      super(myServer.getProjectManager(), PERSONAL_PRIORITY_CLASS_ID, "Personal", "Contains all personal builds", priority, Collections.emptySet());
    }

    @Override
    public boolean isPersonal() {
      return true;
    }
  }

  private static class PriorityClassElementVisitor extends JDOMElementVisitor {
    private List<Element> myPriorityClassElements;

    PriorityClassElementVisitor(final Element e) {
      super(e);
    }

    @Override
    public boolean processElement(final Element e) {
      if (e.getName().equals(PRIORITY_CLASS_ELEMENT)) {
        if (myPriorityClassElements == null) {
          myPriorityClassElements = new ArrayList<>();
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
        return new ArrayList<>();
      }
    }
  }

  private static class BuildTypeElementVisitor extends JDOMElementVisitor {
    private Set<String> myBuildTypeIds; // do not init it here, because processElement is invoked from constructor

    BuildTypeElementVisitor(Element e) {
      super(e);
    }

    @Override
    public boolean processElement(Element e) {
      if (e.getName().equals(BUILD_TYPE_ELEMENT)) {
        String buildTypeId = e.getAttributeValue(BUILD_TYPE_ID_ATTRIBUTE);
        if (buildTypeId != null && !buildTypeId.isEmpty()) {
          if (myBuildTypeIds == null) {
            myBuildTypeIds = new TreeSet<>();
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
        return new TreeSet<>();
      }
    }
  }

  /**
   * Copy of jetbrains.buildServer.util.JDOMElementVisitor
   */
  private abstract static class JDOMElementVisitor {
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
     * @param e element to process
     * @return false if child elements for given element should not be processed
     */
    public abstract boolean processElement(Element e);
  }

  private static Collection<String> getBuildTypeIds(Collection<SBuildType> buildTypes) {
    return CollectionsUtil.convertCollection(buildTypes, new Converter<String, SBuildType>() {
      @Override
      public String createFrom(@NotNull final SBuildType source) {
        return source.getExternalId();
      }
    });
  }

}
