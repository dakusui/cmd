package com.github.dakusui.cmd.compatut.core;

import org.junit.Test;

import static java.util.Arrays.binarySearch;

public class LeetMedian4{
  @Test
  public void testExample1() {
    System.out.println(findMedianSortedArrays(
        new int[]{1, 3},
        new int[]{2}
    ));
  }

  @Test
  public void testExample2() {
    System.out.println(findMedianSortedArrays(
        new int[]{1, 2},
        new int[]{3, 4}
    ));
  }

  public double findMedianSortedArrays(int[] nums1, int[] nums2) {
    int left = (nums1.length + nums2.length) / 2;
    int med1 = median(nums1, 0, nums1.length);
    left -= nums1.length / 2;
    int insertionsPosition = binarySearch(nums2, 0, nums2.length, med1);
    if (insertionsPosition == 0) {

    } else if (insertionsPosition == nums2.length) {

    }
    return 0;
  }

  private static int median(int[] nums, int beginIndexInclusive, int endIndexExclusive) {
    return nums[(endIndexExclusive - beginIndexInclusive) / 2];
  }
}
