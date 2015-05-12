/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import java.util.Collection;
import java.util.List;
import jetbrains.buildServer.serverSide.SBuildType;
import org.jetbrains.annotations.NotNull;

/**
 * Priority class for build queue
 * @author dmitry.neverov
 */
public interface PriorityClass {

  /**
   * Get priority class id
   * @return priority class id
   */
  @NotNull
  String getId();

  /**
   * Get priority class name
   * @return priority class name
   */
  @NotNull
  String getName();

  /**
   * priority class description
   * @return priority class description
   */
  @NotNull
  String getDescription();

  /**
   * Get priority of this priority class
   * @return priority of this priority class
   */
  int getPriority();

  /**
   * Get build types of this priority class
   * @return build types of this priority class
   */
  @NotNull
  List<SBuildType> getBuildTypes();

  /**
   * Check if this priority class is default priority class
   * @return true if this priority class is default priority class
   */
  boolean isDefaultPriorityClass();

  /**
   * Check if this priority class is priority class for personal builds
   * @return true if this priority class is priority class for personal builds 
   */
  boolean isPersonal();

  @NotNull
  PriorityClass addBuildTypes(@NotNull Collection<String> buildTypeIds);

  @NotNull
  PriorityClass removeBuildTypes(@NotNull Collection<String> buildTypeIds);

  @NotNull
  PriorityClass setPriority(int priority);

  @NotNull
  PriorityClass update(@NotNull String name, @NotNull String description, int priority);

  @NotNull
  PriorityClass updateExternalId(@NotNull String oldExternalId, @NotNull String newExternalId);
}
