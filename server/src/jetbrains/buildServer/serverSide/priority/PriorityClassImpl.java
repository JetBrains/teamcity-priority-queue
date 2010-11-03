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

import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassDescriptionException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassPriorityException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author dmitry.neverov
 */
public class PriorityClassImpl implements PriorityClass, Comparable<PriorityClassImpl> {

  private final ProjectManager myProjectManager;
  private final String myId;
  private final String myName;
  private final String myDescription;
  private final int myPriority;
  private final Set<String> myBuildTypeIds;

  public PriorityClassImpl(@NotNull ProjectManager projectManager,
                           @NotNull String id,
                           @NotNull String name,
                           @NotNull String description,
                           int priority,
                           @NotNull Collection<String> buildTypeIds) throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException {
    checkNameIsCorrect(name);
    checkDescriptionIsCorrect(description);
    checkPriorityIsCorrect(priority);
    myProjectManager = projectManager;
    myId = id;
    myName = name;
    myDescription = description;
    myPriority = priority;
    myBuildTypeIds = new HashSet<String>(buildTypeIds);
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
    return new ArrayList<SBuildType>(myProjectManager.findBuildTypes(myBuildTypeIds));
  }

  public Set<String> getBuildTypeIds() {
    return new HashSet<String>(myBuildTypeIds);
  }

  public boolean isDefault() {
    return false;
  }

  public boolean isPersonal() {
    return false;
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
      throw new InvalidPriorityClassNameException("Name must be specified");
    }
    if (name.trim().length() > 255) {
      throw new InvalidPriorityClassNameException("Name is too long");
    }
  }

  public static void checkDescriptionIsCorrect(@NotNull String description) {
    if (description.length() > 2000) {
      throw new InvalidPriorityClassDescriptionException("Description is too long");
    }
  }

  public static void checkPriorityIsCorrect(int priority) {
    if (priority < -100 || priority > 100) {
      throw new InvalidPriorityClassPriorityException("Priority should be in interval [-100..100]");
    }
  }
}
