package com.github.dakusui.cmd.core;

public interface Pipeline {
  interface Stage {
    interface Factory {
      Source source();

      Sink sink();

      Mapper map();

      Reducer reduce();

      class Builder {
        Factory build() {
          return new Factory() {
            @Override
            public Source source() {
              return null;
            }

            @Override
            public Sink sink() {
              return null;
            }

            @Override
            public Mapper map() {
              return null;
            }

            @Override
            public Reducer reduce() {
              return null;
            }
          };
        }
      }
    }
  }

  interface Upstream extends Stage{
    void tee(Downstream... downstreams);
  }

  interface Downstream extends Stage {
  }

  interface Source extends Upstream {
  }

  interface Sink extends Downstream {
  }

  interface Pipe extends Upstream, Downstream {
  }

  interface Mapper extends Pipe {
  }

  interface Reducer extends Pipe {
  }
}
