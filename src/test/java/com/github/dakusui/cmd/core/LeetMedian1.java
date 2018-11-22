package com.github.dakusui.cmd.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/*
Example 1:

nums1 = [1, 3]
nums2 = [2]

The median is 2.0
Example 2:

nums1 = [1, 2]
nums2 = [3, 4]

The median is (2 + 3)/2 = 2.5

https://leetcode.com/submissions/detail/190741154/
*/
public class LeetMedian1 {
  @Test
  public void testExample1() {
    System.out.println(findMedianSortedArrays(
        new int[] {1, 3},
        new int[] {2}
    ));
  }

  @Test
  public void testExample2() {
    System.out.println(findMedianSortedArrays(
        new int[] {1, 2},
        new int[] {3, 4}
    ));
  }
  public double findMedianSortedArrays(int[] nums1, int[] nums2) {
    List<Integer> merged = StreamSupport.stream(
        ((Iterable<Integer>) () -> mergingIterator(
            Arrays.stream(nums1).boxed().collect(toList()).iterator(),
            Arrays.stream(nums2).boxed().collect(toList()).iterator()
        )).spliterator(), false).collect(toList());

    return merged.size() % 2 == 0
        ? ((double) merged.get(merged.size() / 2) + (double) merged.get(merged.size() / 2 - 1)) / 2
        : merged.get(merged.size() / 2);
  }

  private static Iterator<Integer> mergingIterator(Iterator<Integer> a, Iterator<Integer> b) {
    return new Iterator<Integer>() {
      Integer nextA = a.hasNext() ? a.next() : null;
      Integer nextB = b.hasNext() ? b.next() : null;

      @Override
      public boolean hasNext() {
        return !(nextA == null && nextB == null);
      }

      @Override
      public Integer next() {
        if (nextA != null && nextB != null) {
          if (nextA <= nextB) {
            return nextA();
          } else {
            return nextB();
          }
        }
        if (nextA == null && nextB != null)
          return nextB();
        if (nextA != null && nextB == null)
          return nextA();
        throw new NoSuchElementException();
      }

      private Integer nextB() {
        try {
          return nextB;
        } finally {
          nextB = b.hasNext() ? b.next() : null;
        }
      }

      private Integer nextA() {
        try {
          return nextA;
        } finally {
          nextA = a.hasNext() ? a.next() : null;
        }
      }
    };
  }
}
