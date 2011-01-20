/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import jetbrains.buildServer.util.CollectionsUtil;
import jetbrains.buildServer.util.Converter;
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
  //Next 3 maps use SQueuedBuild.getItemId() as keys, because SQueuedBuild doesn't implement equals and hashCode:
  private final Map<String, Double> myItemWeights = new HashMap<String, Double>();
  private final Map<String, Integer> myMovedItemsPriorities = new HashMap<String, Integer>();
  private final Map<String, Integer> myPrioritiesOnTheInsertMoment = new HashMap<String, Integer>();
  private List<SQueuedBuild> myLastResult = new ArrayList<SQueuedBuild>();
  private double myPriorityCoefficient;
  private final PriorityClassManager myPriorityClassManager;
  private final BuildQueue myBuildQueue;

  public BuildQueuePriorityOrdering(@NotNull final SBuildServer server,
                                    @NotNull final PriorityClassManager priorityClassManager) {
    myBuildQueue = server.getQueue();
    myPriorityClassManager = priorityClassManager;
    myPriorityCoefficient = parseDouble(TeamCityProperties.getProperty("teamcity.buildqueue.priorityWeight", "1.0"));
  }

  /*
   * This method is called under sync of BuildQueue
   */
  @NotNull
  public List<SQueuedBuild> addBuilds(@NotNull final List<SQueuedBuild> itemsToAdd,
                                      @NotNull final List<SQueuedBuild> currentQueueItems) {
    try {
      clearDataOfRemovedItems(currentQueueItems);
      checkHaveRequiredDataOnCurrentItems(currentQueueItems);
      updateWeights(currentQueueItems);
      addNewItems(itemsToAdd, currentQueueItems);
      myLastResult = new ArrayList<SQueuedBuild>(currentQueueItems);
      return currentQueueItems;
    } catch (Throwable t) {
      myLogger.error("Error while compute new queue order", t);
      return Collections.emptyList();
    }
  }

  private void addNewItems(final List<SQueuedBuild> itemsToAdd, final List<SQueuedBuild> currentQueueItems) {
    for (SQueuedBuild item: itemsToAdd) {
      int buildTypePriority = getCurrentBuildTypePriority(item);
      double weight = myPriorityCoefficient * buildTypePriority;
      int position = getNewItemPosition(weight, currentQueueItems);
      currentQueueItems.add(position, item);
      myItemWeights.put(item.getItemId(), weight);
      myPrioritiesOnTheInsertMoment.put(item.getItemId(), buildTypePriority);
      logItemAdded(currentQueueItems, item, position, weight);
    }
  }

  private void logItemAdded(final List<SQueuedBuild> items, final SQueuedBuild item, final int position, final double weight) {
    final int defaultPosition = items.size() - 1; //default position is in the end of the queue, minus 1 because item already added
    if (myLogger.isDebugEnabled()) {
      myLogger.debug("Current item priorities: " + myItemWeights + ", new item " + item + " with weight " + weight + " inserted at position " + position);
    } else if (myLogger.isInfoEnabled()) {
      if (position != defaultPosition) {
        SQueuedBuild previousItem = null;
        if (position > 0) {
          previousItem = items.get(position - 1);
        }
        SQueuedBuild nextItem = items.get(position + 1);
        if (previousItem != null) {
          Double previousItemWeight = getItemWeight(previousItem.getItemId());
          Double nextItemWeight = getItemWeight(nextItem.getItemId());
          myLogger.info(String.format("New item %s with weight %.2f inserted at position %d instead of %d, between items %s (weight %.2f) and %s (weight %.2f)",
                  item.toString(), weight, position, defaultPosition, previousItem, previousItemWeight, nextItem, nextItemWeight));
        } else {
          Double nextItemWeight = getItemWeight(nextItem.getItemId());
          myLogger.info(String.format("New item %s with weight %.2f inserted at position %d instead of %d, before item %s (weight %.2f)",
                  item.toString(), weight, position, defaultPosition, nextItem, nextItemWeight));
        }
      } else {
        myLogger.info(String.format("New item %s with weight %.2f inserted at the default position %d in the end of the queue",
                  item.toString(), weight, position));
      }
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
      if (newItemWeight <= getItemWeight(currentQueueItems.get(i).getItemId())) {
        return i + 1;
      }
    }
    return 0;
  }

  /**
   * Delete data about queued builds removed from queue.
   * When this method returns myItemWeights, myMovedItemsPriorities, myLastResult and myPrioritiesOnTheInsertMoment
   * contain only data for currentQueueItems.
   * @param currentQueueItems
   */
  private void clearDataOfRemovedItems(List<SQueuedBuild> currentQueueItems) {
    List<String> currentItemIds = CollectionsUtil.convertCollection(currentQueueItems, new Converter<String, SQueuedBuild>() {
      public String createFrom(@NotNull SQueuedBuild source) {
        return source.getItemId();
      }
    });
    myItemWeights.keySet().retainAll(currentItemIds);
    myMovedItemsPriorities.keySet().retainAll(currentItemIds);
    myLastResult.retainAll(currentQueueItems);
    myPrioritiesOnTheInsertMoment.keySet().retainAll(currentItemIds);
  }

  /**
   * Should be called after clearDataOfRemovedItems()
   */
  private void checkHaveRequiredDataOnCurrentItems(List<SQueuedBuild> currentQueueItems) {
    int currentQueueSize = currentQueueItems.size();
    //it is enough to check size because these maps contains only elements from currentQueueItems
    //after a call to clearDataOfRemovedItems()
    if (myItemWeights.size() != currentQueueSize || myPrioritiesOnTheInsertMoment.size() != currentQueueSize) {
      myLogger.error(String.format("Priority ordering plugin do not have a data on some items in build queue. " +
                                   this.toString() + " Current queue items=%s.", currentQueueItems));
    }
  }

  /**
   * Recalculate queued builds weights according to movements in the build queue and theirs wait times.
   * @param currentQueueItems current state of build queue
   */
  private void updateWeights(List<SQueuedBuild> currentQueueItems) {
    updateMovedItemsPriorities(currentQueueItems);
    Date now = new Date();
    for (Map.Entry<String, Double> entry: myItemWeights.entrySet()) {
      String itemId = entry.getKey();
      SQueuedBuild queuedBuild = myBuildQueue.findQueued(itemId);
      if (queuedBuild != null) {
        entry.setValue(getItemWeightAtTheMoment(queuedBuild, now));
      } else {
        throw new IllegalStateException(String.format("Cannot find queued build with itemId=%s", itemId));
      }
    }
  }

  /**
   * Recalculate priorities of moved queued builds. Each moved queued build get priority of
   * queued build which place it holds in new order.
   * @param newQueueOrder new order of build queue
   */
  private void updateMovedItemsPriorities(List<SQueuedBuild> newQueueOrder) {
    if (!myLastResult.isEmpty()) {
      for (int i = 0; i < myLastResult.size(); i++) {
        SQueuedBuild lastResultItem = myLastResult.get(i);
        SQueuedBuild newOrderItem = newQueueOrder.get(i);
        if (!lastResultItem.getItemId().equals(newOrderItem.getItemId())) {
          myMovedItemsPriorities.put(newOrderItem.getItemId(), getBuildTypePriorityOnTheInsertMoment(lastResultItem));
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
    Integer movedItemPriority = myMovedItemsPriorities.get(item.getItemId());
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
    Integer priorityOnTheMomentOfInsert = myPrioritiesOnTheInsertMoment.get(item.getItemId());
    if (priorityOnTheMomentOfInsert != null) {
      return priorityOnTheMomentOfInsert;
    } else {
      myLogger.error("Item " + item.toString() + " was added, but it's build type priority on the insert moment is lost");
      return 0;
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
  Map<String, Double> getCurrentPriorities() {
    return myItemWeights;
  }

  public String toString() {
    return String.format("BuildQueuePriorityOrdering state: myItemWeights=%s, myMovedItemsPriorities=%s, myPrioritiesOnTheInsertMoment=%s, myLastResult=%s.",
                         myItemWeights, myMovedItemsPriorities, myPrioritiesOnTheInsertMoment, myLastResult);
  }

  private double parseDouble(String priorityCoefficientString) {
    try {
      return Double.parseDouble(priorityCoefficientString);
    } catch (NumberFormatException e) {
      return 1.0;
    }
  }

  private Double getItemWeight(String itemId) {
    Double weight = myItemWeights.get(itemId);
    if (weight != null) {
      return weight;
    } else {
      myLogger.error("Item itemId=" + itemId + " was added, but it's weight is lost");
      return 0.0;
    }
  }
}