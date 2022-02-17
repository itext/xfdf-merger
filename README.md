# XFDF annotation merging tool

## Summary

This repository contains an experimental command-line tool to merge XFDF
annotations into a PDF document. It's specifically geared towards annotations
used for document review and errata processing.

The merge tool will process annotations of the following types:

 - **Caret**
 - **Text**
 - **Highlight**
 - **Squiggly**
 - **Underline**
 - **Strikethrough**
 - **Stamp**
 - **FreeText**

The positioning of the annotations in the final document can be manipulated
to a degree. Concretely, the tool supports

 - shifting the page numbers to which the annotations are applied;
 - scaling the annotations' coordinates by a constant factor;
 - translating the annotations' coordinates by a constant vector.

## Building

The project builds with Maven. After cloning, run the following command:

```bash
mvn package
```

If the build finishes successfully, the `target` folder should contain two
`.jar` files: `xfdf-merge-<VERSION>.jar` and
`xfdf-merge-<VERSION>-jar-with-dependencies.jar`. Both are executable JAR
files, but the latter includes all relevant dependencies.


## Usage

After building, the merge tool can be invoked as follows:

```bash
./xfdfmerge.sh input.pdf input.xfdf output.pdf [transform]
```

Parameters:

 - `input.pdf`: the input PDF document;
 - `input.xfdf`: the XFDF document containing the annotations to apply;
 - `output.pdf`: the output PDF document (will be overwritten);
 - `transform`: an optional transformation string (see below).

The optional transformation string takes the form
`PGNUMSHIFT/XSHIFT/YSHIFT/SCALE`. If a transformation string is supplied, all
parts must be present. They are processed as follows:

 - `PGNUMSHIFT`: constant integer added to the page numbers in the XFDF file
    before applying annotations;
 - `XSHIFT,YSHIFT,SCALE`: geometric transformation parameters applied to 
   annotations before rendering.

More precisely, the following affine transformation is applied to all
annotation coordinates: `(x, y) -> (SCALE * x + XSHIFT, SCALE * y + YSHIFT)`.
In other words, the effective default value of `transform` is `0/0/0/1`.


## Disclaimer

This is an experimental tool, not an iText product. It is provided to the
community under the terms of the AGPL (see [LICENSE](LICENSE.md)) on an as-is
basis.