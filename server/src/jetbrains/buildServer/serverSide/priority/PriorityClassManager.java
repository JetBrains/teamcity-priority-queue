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

import java.util.List;
import java.util.Set;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.priority.exceptions.DuplicatePriorityClassNameException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassDescriptionException;
import jetbrains.buildServer.serverSide.priority.exceptions.InvalidPriorityClassNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maintains priority classes 
 */
public interface PriorityClassManager {

  /**
   * Get set of all priority classes configured in the system
   * @return set of all priority classes configured in the system
   */
  @NotNull
  List<PriorityClass> getAllPriorityClasses();

  /**
   * Find priority class by id
   * @param priorityClassId priority class id
   * @return priority class with specified id or null
   */
  @Nullable
  PriorityClass findPriorityClassById(@NotNull String priorityClassId);

  /**
   * Find priority class by name
   * @param priorityClassName priority class name
   * @return priority class with specified name or null
   */
  @Nullable
  PriorityClass findPriorityClassByName(@NotNull String priorityClassName);

  @NotNull
  PriorityClass getBuildTypePriorityClass(@NotNull SBuildType buildType);

  /**
   * Create new priority class
   * @param name priority class name
   * @param description priority class description
   * @param priority priority class priority
   * @return newly created priority class
   * @throws InvalidPriorityClassNameException if <code>name</code> cannot be name for priority class
   * @throws InvalidPriorityClassDescriptionException if <code>description</code> cannot be description for priority class
   * @throws DuplicatePriorityClassNameException if priority class <code>name</code> already exists
   */
  @NotNull
  PriorityClass createPriorityClass(@NotNull String name, @NotNull String description, int priority)
          throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException, DuplicatePriorityClassNameException;

  /**
   * Create new priority class
   * @param name priority class name
   * @param description priority class description
   * @param priority priority class priority
   * @param buildTypes priority class build types
   * @return newly created priority class
   * @throws InvalidPriorityClassNameException if <code>name</code> cannot be name for priority class
   * @throws InvalidPriorityClassDescriptionException if <code>description</code> cannot be description for priority class
   * @throws DuplicatePriorityClassNameException if priority class <code>name</code> already exists
   */
  @NotNull
  PriorityClass createPriorityClass(@NotNull String name, @NotNull String description, int priority, @NotNull Set<SBuildType> buildTypes)
          throws InvalidPriorityClassNameException, InvalidPriorityClassDescriptionException, DuplicatePriorityClassNameException;

  /**
   * Save changed priority class
   * @param priorityClass priority class to save
   * @throws DuplicatePriorityClassNameException if priority class <code>name</code> already exists
   */
  void savePriorityClass(@NotNull PriorityClass priorityClass) throws DuplicatePriorityClassNameException;

  /**
   * Delete priority class by id
   * @param priorityClassId priority class id
   */
  void deletePriorityClass(@NotNull String priorityClassId);

  /**
   * Check if priority class is default priority class
   * @param priorityClass priority class to check
   * @return true if it is default
   */
  boolean isDefaultPriorityClass(@NotNull PriorityClass priorityClass);

  /**
   * Check if priority class is priority class for personal builds
   * @param priorityClass priority class to check
   * @return true if it is priority class for personal builds
   */
  boolean isPersonalPriorityClass(@NotNull PriorityClass priorityClass);

  /**
   * Get default priority class which holds all build types not included into any other priority class
   * @return special default priority class
   */
  @NotNull
  PriorityClass getDefaultPriorityClass();

  /**
   * Get priority class for personal builds
   * @return priority class for personal builds
   */
  @NotNull
  PriorityClass getPersonalPriorityClass();

}
