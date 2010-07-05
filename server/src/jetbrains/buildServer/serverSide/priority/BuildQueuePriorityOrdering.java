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

import jetbrains.buildServer.serverSide.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import java.util.*;

/**
 * BuildQueue ordering strategy based on build type priorities
 * @author dmitry.neverov
 */
public final class BuildQueuePriorityOrdering implements BuildQueueOrderingStrategy {

  private static final long DEFAULT_DURATION = 10 * 60;//10 minutes

  private Logger myLogger = Logger.getLogger(BuildQueuePriorityOrdering.class.getName());
  private final Map<SQueuedBuild, Double> myItemWeights = new HashMap<SQueuedBuild, Double>();
  private final Map<SQueuedBuild, Integer> myMovedItemsPriorities = new HashMap<SQueuedBuild, Integer>();
  private final Map<SQueuedBuild, Integer> myPrioritiesOnTheInsertMoment = new HashMap<SQueuedBuild, Integer>();
  private List<SQueuedBuild> myLastResult = new ArrayList<SQueuedBuild>();
  private double myPriorityCoefficient;
  private final PriorityClassManager myPriorityClassManager;

  public BuildQueuePriorityOrdering(@NotNull final PriorityClassManager priorityClassManager) {
    myPriorityCoefficient = parseDouble(TeamCityProperties.getProperty("teamcity.buildqueue.priorityWeight", "1.0"));
    myPriorityClassManager = priorityClassManager;
  }

  /*
   * This method is called under sync of BuildQueue
   */
  @NotNull
  public List<SQueuedBuild> addBuilds(@NotNull final List<SQueuedBuild> itemsToAdd,
                                      @NotNull final List<SQueuedBuild> currentQueueItems) {
    try {
      clearDataOfRemovedItems(currentQueueItems);
      updateWeights(currentQueueItems);
      for (SQueuedBuild newItem: itemsToAdd) {
        int defaultNewItemPosition = currentQueueItems.size();
        int newItemBuildTypePriority = getCurrentBuildTypePriority(newItem);
        double newItemWeight = myPriorityCoefficient * newItemBuildTypePriority;
        int newItemPosition = getNewItemPosition(newItemWeight, currentQueueItems);
        currentQueueItems.add(newItemPosition, newItem);
        myItemWeights.put(newItem, newItemWeight);
        myPrioritiesOnTheInsertMoment.put(newItem, newItemBuildTypePriority);
        if (myLogger.isDebugEnabled()) {
          myLogger.debug("Current item priorities: " + myItemWeights +
                  ", new item " + newItem + " with weight " + newItemWeight +
                  " inserted at position " + newItemPosition);
        } else if (myLogger.isInfoEnabled()) {
          if (newItemPosition != defaultNewItemPosition) {
            SQueuedBuild previousItem = null;
            if (newItemPosition > 0) {
              previousItem = currentQueueItems.get(newItemPosition - 1);
            }
            SQueuedBuild nextItem = currentQueueItems.get(newItemPosition + 1);
            if (previousItem != null) {
              Double previousItemWeight = myItemWeights.get(previousItem);
              Double nextItemWeight = myItemWeights.get(nextItem);
              myLogger.info(String.format("New item %s with weight %.2f inserted at position %d instead of %d, between items %s (weight %.2f) and %s (weight %.2f)",
                      newItem.toString(), newItemWeight, newItemPosition, defaultNewItemPosition,
                      previousItem, previousItemWeight, nextItem, nextItemWeight));
            } else {
              Double nextItemWeight = myItemWeights.get(nextItem);
              myLogger.info(String.format("New item %s with weight %.2f inserted at position %d instead of %d, before item %s (weight %.2f)",
                      newItem.toString(), newItemWeight, newItemPosition, defaultNewItemPosition,
                      nextItem, nextItemWeight));
            }
          } else {
            myLogger.info(String.format("New item %s with weight %.2f inserted at the default position %d in the end of the queue",
                      newItem.toString(), newItemWeight, newItemPosition));
          }
        }
      }
      myLastResult = new ArrayList<SQueuedBuild>(currentQueueItems);
      return currentQueueItems;
    } catch (Throwable t) {
      myLogger.error("Error while compute new queue order", t);
      return Collections.emptyList();
    }
  }

  private double parseDouble(String priorityCoefficientString) {
    try {
      return Double.parseDouble(priorityCoefficientString);
    } catch (NumberFormatException e) {
      return 1.0;
    }
  }

  /**
   * Get new item position according to it's weight and weights of other items
   * @param newItemWeight weight of new item
   * @param currentQueueItems current state of the queue
   * @return position there new item should be inserted
   */
  private int getNewItemPosition(double newItemWeight, List<SQueuedBuild> currentQueueItems) {
    //move up until first item with higher or equal priority
    for (int i = currentQueueItems.size() - 1; i >= 0; i--) {
      if (newItemWeight <= myItemWeights.get(currentQueueItems.get(i))) {
        return i + 1;
      }
    }
    return 0;
  }

  /**
   * Delete data about queued builds removed from queue
   * @param currentQueueItems
   */
  private void clearDataOfRemovedItems(List<SQueuedBuild> currentQueueItems) {
    myItemWeights.keySet().retainAll(currentQueueItems);
    myMovedItemsPriorities.keySet().retainAll(currentQueueItems);
    myLastResult.retainAll(currentQueueItems);
    myPrioritiesOnTheInsertMoment.keySet().retainAll(currentQueueItems);
  }

  /**
   * Recalculate queued builds weights according to movements in the build queue and theirs wait times.
   * @param currentQueueItems current state of build queue
   */
  private void updateWeights(List<SQueuedBuild> currentQueueItems) {
    updateMovedItemsPriorities(currentQueueItems);
    Date now = new Date();
    for (Map.Entry<SQueuedBuild, Double> entry: myItemWeights.entrySet()) {
      entry.setValue(getItemWeightAtTheMoment(entry.getKey(), now));
    }
  }

  /**
   * Recalculate priorities of moved queued builds. Each moved queued build get priority of
   * queued build which place it holds in new order.
   * @param newQueueOrder new order of build queue
   */
  private void updateMovedItemsPriorities(List<SQueuedBuild> newQueueOrder) {
    if (!myLastResult.isEmpty()) {
      assert myLastResult.size() == newQueueOrder.size();
      for (int i = 0; i < myLastResult.size(); i++) {
        SQueuedBuild lastResultItem = myLastResult.get(i);
        SQueuedBuild newOrderItem = newQueueOrder.get(i);
        if (!lastResultItem.equals(newOrderItem)) {
          myMovedItemsPriorities.put(newOrderItem, getBuildTypePriorityOnTheInsertMoment(lastResultItem));
        }
      }
    }
  }

  /**
   * Get weight for queued item at the moment
   * @param item queued item
   * @param moment moment in time
   * @return weight for item at the moment
   */
  private double getItemWeightAtTheMoment(SQueuedBuild item, Date moment) {
    long durationSeconds = getDurationSeconds(item);
    long waitMilliseconds = moment.getTime() - item.getWhenQueued().getTime();
    return waitMilliseconds / (durationSeconds * 1000.0) + myPriorityCoefficient * getEffectiveBuildTypePriority(item);
  }

  /**
   * Get queued item estimate duration in seconds
   * @param item queue item
   * @return queued item estimate duration or DEFAULT_DURATION if estimate duration could not be calculated  
   */
  private long getDurationSeconds(SQueuedBuild item) {
    BuildEstimates estimates = item.getBuildEstimates();
    if (estimates == null) return DEFAULT_DURATION;
    TimeInterval timeInterval = estimates.getTimeInterval();
    if (timeInterval == null) return DEFAULT_DURATION;
    Long duration = timeInterval.getDurationSeconds();
    if (duration == null) return DEFAULT_DURATION;
    return duration;
  }

  /**
   * Get priority of queued build
   * @param item item of interest
   * @return priority of item's build type or if item was moved it's recomputed priority
   */
  private int getEffectiveBuildTypePriority(SQueuedBuild item) {
    Integer movedItemPriority = myMovedItemsPriorities.get(item);
    if (movedItemPriority != null) {
      return movedItemPriority;
    } else {
      return getBuildTypePriorityOnTheInsertMoment(item);
    }
  }

  /**
   * Get item build type priority on the moment when it was added to the queue
   * @param item queued item
   * @return priority of item build type on the moment when it was added to the queue
   */
  private int getBuildTypePriorityOnTheInsertMoment(SQueuedBuild item) {
    Integer priorityOnTheMomentOfInsert = myPrioritiesOnTheInsertMoment.get(item);
    if (priorityOnTheMomentOfInsert != null) {
      return priorityOnTheMomentOfInsert;
    } else {
      throw new Error("Item " + item.toString() + " was added, but it's build type priority on the insert moment is lost");
    }
  }

  private int getCurrentBuildTypePriority(SQueuedBuild item) {
    if (item.isPersonal()) {
      return myPriorityClassManager.getPersonalPriorityClass().getPriority();
    } else {
      return myPriorityClassManager.getBuildTypePriorityClass(item.getBuildType()).getPriority();
    }
  }

  /**
   * For tests only
   * @return current build queue items priorities
   */
  Map<SQueuedBuild, Double> getCurrentPriorities() {
    return myItemWeights;
  }
}