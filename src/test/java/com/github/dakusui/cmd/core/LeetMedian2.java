package com.github.dakusui.cmd.core;

import org.junit.Test;

import java.util.Arrays;

/**
 * https://leetcode.com/submissions/detail/190742740/
 */
public class LeetMedian2 {
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
    int[] work = new int[nums1.length + nums2.length];
    System.arraycopy(nums1, 0, work, 0, nums1.length);
    System.arraycopy(nums2, 0, work, nums1.length, nums2.length);
    Arrays.sort(work);
    return work.length % 2 == 0
        ? ((double)work[work.length / 2] + (double) work[work.length / 2 - 1]) / 2
        : work[work.length / 2];
  }
}
