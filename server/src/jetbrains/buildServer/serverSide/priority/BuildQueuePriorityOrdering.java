

package jetbrains.buildServer.serverSide.priority;

import java.util.*;
import java.util.stream.Collectors;
import jetbrains.buildServer.serverSide.*;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;

/**
 * BuildQueue ordering strategy based on build type priorities
 * @author dmitry.neverov
 */
public final class BuildQueuePriorityOrdering implements BuildQueueOrderingStrategy {

  private static final long DEFAULT_DURATION = 10 * 60;//10 minutes

  private final Logger myLogger = Logger.getLogger(BuildQueuePriorityOrdering.class.getName());
  //Next 3 maps use SQueuedBuild.getItemId() as keys, because SQueuedBuild doesn't implement equals and hashCode:
  private final Map<String, Double> myItemWeights = new HashMap<String, Double>();
  private final Map<String, Integer> myMovedItemsPriorities = new HashMap<String, Integer>();
  private final Map<String, Integer> myPrioritiesOnTheInsertMoment = new HashMap<String, Integer>();
  private List<SQueuedBuild> myLastResult = new ArrayList<SQueuedBuild>();
  private final double myPriorityCoefficient;
  private final double myWaitCoefficient;
  private final PriorityClassManager myPriorityClassManager;
  private final BuildQueue myBuildQueue;

  public BuildQueuePriorityOrdering(@NotNull final BuildQueue queue,
                                    @NotNull final PriorityClassManager priorityClassManager) {
    myBuildQueue = queue;
    myPriorityClassManager = priorityClassManager;
    myPriorityCoefficient = parseDouble(TeamCityProperties.getProperty("teamcity.buildqueue.priorityWeight", "1.0"));
    myWaitCoefficient = parseDouble(TeamCityProperties.getProperty("teamcity.buildqueue.waitWeight", "1.0"));
  }

  @NotNull
  public synchronized List<SQueuedBuild> addBuilds(@NotNull final List<SQueuedBuild> itemsToAdd,
                                                   @NotNull final List<SQueuedBuild> currentQueueItems) {
    if (!TeamCityProperties.getBooleanOrTrue("teamcity.buildQueue.priorityOrdering.enabled")) return Collections.emptyList();

    try {
      clearDataOfRemovedItems(currentQueueItems);
      ensureHaveDataOnCurrentItems(currentQueueItems);
      updateWeights(currentQueueItems);
      addNewItems(itemsToAdd, currentQueueItems);
      myLastResult = new ArrayList<SQueuedBuild>(currentQueueItems);
      return currentQueueItems;
    } catch (Throwable t) {
      myLogger.error("Error while compute new queue order", t);
      return Collections.emptyList();
    }
  }

  @Override
  public synchronized void restoreQueue(@NotNull final List<SQueuedBuild> queuedBuilds) {
    try {
      myItemWeights.clear();
      myMovedItemsPriorities.clear();
      myPrioritiesOnTheInsertMoment.clear();
      myLastResult.clear();

      final List<SQueuedBuild> result = new ArrayList<>();
      for (SQueuedBuild item: queuedBuilds) {
        int buildTypePriority = getCurrentBuildTypePriority(item);
        double weight = myPriorityCoefficient * buildTypePriority;
        int position = getNewItemPosition(weight, result);
        result.add(position, item);
        myItemWeights.put(item.getItemId(), weight);
        myPrioritiesOnTheInsertMoment.put(item.getItemId(), buildTypePriority);
      }
      myLastResult = new ArrayList<SQueuedBuild>(result);
    } catch (Throwable t) {
      myLogger.error("Error while compute new queue order", t);
    }
  }

  private void addNewItems(@NotNull final List<SQueuedBuild> itemsToAdd, @NotNull final List<SQueuedBuild> currentQueueItems) {
    Set<String> buildIds = getIds(currentQueueItems);
    for (SQueuedBuild item: itemsToAdd) {
      if (buildIds.contains(item.getItemId())) {
        myLogger.info("The current queue items alredy contain the build " + item + ", don't add it to the priority order");
        continue;
      }
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
          myLogger.info(String.format(Locale.ENGLISH, "New item %s with weight %.2f inserted at position %d instead of %d, between items %s (weight %.2f) and %s (weight %.2f)",
                  item.toString(), weight, position, defaultPosition, previousItem, previousItemWeight, nextItem, nextItemWeight));
        } else {
          Double nextItemWeight = getItemWeight(nextItem.getItemId());
          myLogger.info(String.format(Locale.ENGLISH, "New item %s with weight %.2f inserted at position %d instead of %d, before item %s (weight %.2f)",
                  item.toString(), weight, position, defaultPosition, nextItem, nextItemWeight));
        }
      } else {
        myLogger.info(String.format(Locale.ENGLISH, "New item %s with weight %.2f inserted at the default position %d in the end of the queue",
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
  private void clearDataOfRemovedItems(@NotNull List<SQueuedBuild> currentQueueItems) {
    Set<String> currentItemIds = getIds(currentQueueItems);
    myItemWeights.keySet().retainAll(currentItemIds);
    myMovedItemsPriorities.keySet().retainAll(currentItemIds);
    myPrioritiesOnTheInsertMoment.keySet().retainAll(currentItemIds);

    List<SQueuedBuild> newResult = new ArrayList<>();
    for (SQueuedBuild qb: myLastResult) {
      if (currentItemIds.contains(qb.getItemId())) {
        newResult.add(qb);
      }
    }
    myLastResult = newResult;
  }


  @NotNull
  private Set<String> getIds(@NotNull List<SQueuedBuild> currentQueueItems) {
    return currentQueueItems.stream().map(i -> i.getItemId()).collect(Collectors.toSet());
  }

  //Should be called after clearDataOfRemovedItems()
  private void ensureHaveDataOnCurrentItems(List<SQueuedBuild> items) {
    for (SQueuedBuild item : items) {
      String itemId = item.getItemId();
      Integer priority = myPrioritiesOnTheInsertMoment.get(itemId);
      if (priority == null) {
        priority = getCurrentBuildTypePriority(item);
        myLogger.warn("Cannot find priority of the item " + item + ", use default = " + priority);
        myPrioritiesOnTheInsertMoment.put(itemId, priority);
      }
      Double weight = myItemWeights.get(itemId);
      if (weight == null) {
        weight = myPriorityCoefficient * priority;
        myLogger.warn("Cannot find weight of the item " + item + ", use default = " + weight);
        myItemWeights.put(itemId, weight);
      }
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
    if (myLastResult.size() > newQueueOrder.size())
      myLogger.warn("Wrong queued builds, last result: " + myLastResult + ", new order: " + newQueueOrder);
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
    double durationMillis = getDurationSeconds(item) * 1000.0;
    long waitMillis = moment.getTime() - item.getWhenQueued().getTime();
    double waitPart = myWaitCoefficient * waitMillis / durationMillis;
    double configPart = myPriorityCoefficient * getEffectiveBuildTypePriority(item);
    if (Double.isNaN(waitPart)) {
      return configPart;
    } else {
      return waitPart + configPart;
    }
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
    return duration == 0 ? 1 : duration;
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
      try {
        return myPriorityClassManager.getBuildTypePriorityClass(item.getBuildType()).getPriority();
      } catch (BuildTypeNotFoundException e) {
        return 0;
      }
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