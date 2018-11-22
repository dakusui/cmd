package com.github.dakusui.cmd.core;

import org.junit.Test;

/**
 * https://leetcode.com/submissions/detail/190744863/
 */
public class LeetMedian3 {
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
    int cur1 = 0, cur2 = 0;

    for (int i = 0; i < work.length; i++) {
      if (cur1 >= nums1.length)
        work[i] = nums2[cur2++];
      else if (cur2 >= nums2.length)
        work[i] = nums1[cur1++];
      else
        work[i] = nums1[cur1] <= nums2[cur2]
            ? nums1[cur1++]
            : nums2[cur2++];
    }

    return work.length % 2 == 0
        ? ((double) work[work.length / 2] + (double) work[work.length / 2 - 1]) / 2
        : work[work.length / 2];
  }
}
