package com.github.dakusui.cmd.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

public enum IoUtils {
  ;

  static BufferedReader bufferedReader(InputStream is, Charset charset) {
    return new BufferedReader(new InputStreamReader(is, charset));
  }

  static String readLineFrom(BufferedReader br) {
    try {
      return br.readLine();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
