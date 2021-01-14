/*
 * Copyright 2000-2021 JetBrains s.r.o.
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

import java.util.*;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassDescriptionException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassPriorityException;
import org.jetbrains.annotations.NotNull;

/**
 * @author dmitry.neverov
 */
public class PriorityClassImpl implements PriorityClass, Comparable<PriorityClassImpl> {

  private final ProjectManager myProjectManager;
  private final String myId;
  private final String myName;
  private final String myDescription;
  private final int myPriority;
  private final Set<String> myExternalIds;

  public PriorityClassImpl(@NotNull ProjectManager projectManager,
                           @NotNull String id,
                           @NotNull String name,
                           @NotNull String description,
                           int priority,
                           @NotNull Collection<String> externalIds) throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException {
    checkNameIsCorrect(name);
    checkDescriptionIsCorrect(description);
    checkPriorityIsCorrect(priority);
    myProjectManager = projectManager;
    myId = id;
    myName = name;
    myDescription = description;
    myPriority = priority;
    myExternalIds = new TreeSet<String>(externalIds);
  }

  @NotNull
  public String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  public int getPriority() {
    return myPriority;
  }

  @NotNull
  public List<SBuildType> getBuildTypes() {
    List<SBuildType> bts = new ArrayList<SBuildType>();
    for (String externalId : getExternalIds()) {
      SBuildType bt = myProjectManager.findBuildTypeByExternalId(externalId);
      if (bt != null)
        bts.add(bt);
    }
    return bts;
  }

   Set<String> getExternalIds() {
    return new TreeSet<String>(myExternalIds);
  }

  public boolean isDefaultPriorityClass() {
    return false;
  }

  public boolean isPersonal() {
    return false;
  }

  @NotNull
  public PriorityClass addBuildTypes(@NotNull final Collection<String> buildTypeIds) {
    Set<String> newExternalIds = new HashSet<String>(myExternalIds);
    for (String btId : buildTypeIds) {
      SBuildType bt = myProjectManager.findBuildTypeById(btId);
      if (bt != null)
        newExternalIds.add(bt.getExternalId());
    }
    return new PriorityClassImpl(myProjectManager, myId, myName, myDescription, myPriority, newExternalIds);
  }

  @NotNull
  public PriorityClass removeBuildTypes(@NotNull final Collection<String> buildTypeIds) {
    Set<String> newExternalIds = new HashSet<String>(myExternalIds);
    for (String btId : buildTypeIds) {
      SBuildType bt = myProjectManager.findBuildTypeById(btId);
      if (bt != null)
        newExternalIds.remove(bt.getExternalId());
    }

    //It is possible to miss the extId changed event (e.g. when configuration is changed on disk).
    //In this case collection of our extIds contains an alias of a buildType extId, we need to remove it.
    Set<String> intIds = new HashSet<>(buildTypeIds);
    Set<String> aliasesToRemove = new HashSet<>();
    for (String extId : newExternalIds) {
      SBuildType bt = myProjectManager.findBuildTypeByExternalId(extId);
      if (bt != null && intIds.contains(bt.getBuildTypeId()))
        aliasesToRemove.add(extId);
    }
    newExternalIds.removeAll(aliasesToRemove);

    return new PriorityClassImpl(myProjectManager, myId, myName, myDescription, myPriority, newExternalIds);
  }

  @NotNull
  public PriorityClass setPriority(final int priority) {
    return new PriorityClassImpl(myProjectManager, myId, myName, myDescription, priority, myExternalIds);
  }

  @NotNull
  public PriorityClass update(@NotNull final String name, @NotNull final String description, final int priority) {
    return new PriorityClassImpl(myProjectManager, myId, name, description, priority, getExternalIds());
  }

  @NotNull
  public PriorityClass updateExternalId(@NotNull final String oldExternalId, @NotNull final String newExternalId) {
    Set<String> newExternalIds = new HashSet<String>(myExternalIds);
    newExternalIds.remove(oldExternalId);
    newExternalIds.add(newExternalId);
    return new PriorityClassImpl(myProjectManager, myId, myName, myDescription, myPriority, newExternalIds);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof PriorityClassImpl && myId.equals(((PriorityClassImpl) obj).myId);
  }

  @Override
  public int hashCode() {
    return myId.hashCode();
  }

  @Override
  public String toString() {
    return "PriorityClass [id=" + myId+ ",name=" + myName + "]";
  }

  public int compareTo(PriorityClassImpl other) {
    if (myPriority > other.getPriority()) return 1;
    if (myPriority < other.getPriority()) return -1;
    return myName.compareTo(other.myName);    
  }

  public static void checkNameIsCorrect(@NotNull String name) {
    if (name.trim().length() == 0) {
      throw new InvalidPriorityClassNameException("The name must be specified");
    }
    if (name.trim().length() > 255) {
      throw new InvalidPriorityClassNameException("The name is too long");
    }
  }

  public static void checkDescriptionIsCorrect(@NotNull String description) {
    if (description.length() > 2000) {
      throw new InvalidPriorityClassDescriptionException("The description is too long");
    }
  }

  public static void checkPriorityIsCorrect(int priority) {
    if (priority < -100 || priority > 100) {
      throw new InvalidPriorityClassPriorityException("The priority must be within [-100..100]");
    }
  }
}
