package sks.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import sks.Loader;
import sks.dataset.Dataset;
import sks.dataset.DatasetReader;
import sks.dataset.MalformedRecordException;
import sks.dataset.Record;
import sks.ski.SKIManager;

/**
 * Partitions a spatial dataset using z-order values of objects' coordinates.
 *
 * @author Ariel Cary
 */
public class Partitioner {
  /**
   * Computes the z-order value of a 2D point.
   * @param x longitude.
   * @param y latitude.
   * @return Z-order value of (x, y) represented by an 8-Byte long value.
   */
  private static long getZorderValue(float x, float y) {
    // Get bit representation of coordinates.
    int yInt = Float.floatToIntBits(y);
    int xInt = Float.floatToIntBits(x);
    long z = 0;
    long one = 1;

    // Interleave x and y, putting results in z.
    for (int i = 0; i < 32; i++) {
      int mask = 1 << (31 - i);

      if ((yInt & mask) == mask) {
        z |= one << (31 - i) * 2 + 1;
      }

      if ((xInt & mask) == mask) {
        z |= one << (31 - i) * 2;
      }
    }

    return z;
  }

  /**
   * Picks splitting points from a representative array of samples.
   * The input array of samples is modified by a sort operation.
   *
   * @param samples input array of samples.
   * @param m number of splitting points.
   * @return an array of splitting points.
   */
  private static long[] getSplittingPoints(Long[] samples, int m) {
    if (m < 1 || samples == null || samples.length < m) {
      return null;
    }

    Arrays.sort(samples);
    long[] points = new long[m];
    int step = samples.length / (m + 1);

    for (int i = 1; i <= m; i++) {
      points[i - 1] = samples[i * step - 1];
    }

    return points;
  }

  /**
   * Computes an array of z-order splitting points of a given dataset.
   *
   * @param dataset input dataset.
   * @param m number of splitting points.
   * @return an array of samples.
   * @throws IOException
   */
  private static long[] getSplittingPoints(Dataset dataset, int m)
          throws IOException {
    DatasetReader reader = new DatasetReader(dataset);
    final int SAMPLING_PCT = 3;
    //final int SAMPLING_PCT = 1;
    Record rec = null;
    int size = 0;

    // Get dataset size.
    do {
      try {
        rec = reader.readRecord(false, false);
        size++;
      } catch (MalformedRecordException ex) {
        // Ignore malformed records.
      }
    } while (rec != null);

    reader.close();
    size--;
    int numSamples = size * SAMPLING_PCT / 100;
    Long[] samples = new Long[numSamples];
    int bs = size / numSamples;
    Random rand = new Random();
    reader = new DatasetReader(dataset);
    int records = 0;
    int sampleIndex = 0;

    // Get one sample per block.
    do {
      int target = records + rand.nextInt(bs); // zero based record.
      int boundary = records + bs;

      do {
        try {
          rec = reader.readRecord(false, false);
          records++;
        } catch (MalformedRecordException ex) {
          // Ignore malformed records.
        }
      } while (records < (target + 1) && rec != null);

      if (rec != null) {
        samples[sampleIndex++] = new Long(getZorderValue((float) rec.getLongitude(),
                                  (float) rec.getLatitude()));

        if (sampleIndex == numSamples) {
          break;
        }
      } else {
        // No more records.
        break;
      }

      // Move to the end of block.
      while (rec != null && records < boundary) {
        try {
          rec = reader.readRecord(false, false);
          records++;
        } catch (MalformedRecordException ex) {
          // Ignore malformed records.
        }
      }
    } while (rec != null);

    reader.close();
    return getSplittingPoints(samples, m);
  }

  private static void closeOutputStreams(BufferedOutputStream[] outStreams) {
    if (outStreams != null) {
      for (int i = 0; i < outStreams.length; i++) {
        if (outStreams[i] != null) {
          try {
            outStreams[i].close();
            outStreams[i] = null;
          } catch (IOException e) {
          }
        }
      }

      outStreams = null;
    }
  }

  /**
   * Generate data partitions.
   * 
   * @param category dataset name.
   * @param baseDir dataset parent directory.
   * @param dataset target dataset.
   * @param sPoints splitting points.
   * @param suffix suffix to append to partition names.
   * @return true if partitions were generated successfully, false otherwise.
   */
  private static boolean writePartitions(String category, String baseDir,
          Dataset dataset, long[] sPoints, String suffix) {
    int p = sPoints.length + 1;
    BufferedOutputStream[] outStreams = new BufferedOutputStream[p];
    File headerFile = dataset.getHeaderFile();

    // Create partition output streams and copy header files.
    try {
      for (int i = 0; i < p; i++) {
        String partitionDir = baseDir + "/" + category + "-p" + i;

        if (!(new File(partitionDir)).mkdir()) {
          throw new IOException("Cannot create partition directory: " + partitionDir);
        }

        FileOutputStream fos = new FileOutputStream(partitionDir + "/" +
                                      category + "-p" + i + ".asc" + suffix);
        outStreams[i] = new BufferedOutputStream(fos, Loader.IO_BUFFER_SIZE);
        File partitionHeader = new File(partitionDir + "/" +
                                      category + "-p" + i + ".asc.header" + suffix);

        SKIManager.copyFile(headerFile, partitionHeader);
      }
    } catch (IOException e) {
      System.err.println(e.getMessage());
      closeOutputStreams(outStreams);
      return false;
    }

    DatasetReader reader = null;

    // Populate partitions.
    try {
      reader = new DatasetReader(dataset);
      Record rec = null;

      do {
        try {
          rec = reader.readRecord(false, false);

          if (rec != null) {
            long z = getZorderValue((float) rec.getLongitude(), (float) rec.getLatitude());
            int partitionIndex = Arrays.binarySearch(sPoints, z);

            if (partitionIndex < 0) {
              partitionIndex *= -1;
              partitionIndex -= 1;
            } else if (partitionIndex == p) {
              partitionIndex = p - 1;
            }
            
            byte[] data = rec.getData().getBytes();
            
            if (data != null) {
              outStreams[partitionIndex].write(data);
              outStreams[partitionIndex].write(10);
            }
          }
        } catch (MalformedRecordException e) {
          // Skip record.
        }
      } while (rec != null);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      return false;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
        }
      }

      closeOutputStreams(outStreams);
    }

    return true;
  }

  /**
   * Partitions a dataset. Each partition is saved in an
   * individual subdirectories at the parent data file directory.
   *
   * @param category dataset name.
   * @param dataFile data file.
   * @param headerFile header file.
   * @param p number of partitions.
   * @return true if the dataset was partitioned without errors, else false.
   * @throws IOException
   */
  public static boolean partitionDataset(String category, File dataFile,
                          File headerFile, int p)  {
    try {
      Dataset dataset = new Dataset(dataFile, headerFile);
      long[] sPoints = getSplittingPoints(dataset, p - 1);

      if (sPoints == null || sPoints.length < p - 1) {
        // Not enough splitting points.
        return false;
      }

      return writePartitions(category, dataFile.getParent(), dataset, sPoints, ".tmp");
    } catch (IOException e) {
      System.err.println(e.getMessage());
      return false;
    }
  }

  public static void main(String[] args) {
  //public static void main( ) {
      /*
       if (args.length < 4) {
      System.err.println("Usage: Partitioner <category> <dataset> <header> <partitions>");
      System.exit(1);
    }
       */
 
    /*
    String category = args[0];
    File dataFile = new File(args[1]);
    File headerFile = new File(args[2]);
    int p = 0;
    */
    //
    String category = "pblock2010w";
    File dataFile = new File("d:/pblock2010w.asc");
    File headerFile = new File("d:/pblock2010w.asc.header");
    int p = 12;
//
    try {
      if (!dataFile.exists() || !headerFile.exists() ||
          !dataFile.isFile() || !headerFile.isFile()) {
        throw new IllegalArgumentException("Invalid files.");
      }
      
      //p = Integer.parseInt(args[3]);

      if (p < 2) {
        throw new IllegalArgumentException("Number of partitions must be at least two.");
      }
    } catch (NumberFormatException e) {
      System.err.println("Wrong parameters. Please check and retry. " + e.getMessage());
      System.exit(2);
    } catch (IllegalArgumentException e) {
      System.err.println("Wrong parameters. Please check and retry. " + e.getMessage());
      System.exit(2);
    }

    System.out.println("Dataset partitioning in progress...");

    if (partitionDataset(category, dataFile, headerFile, p)) {
      System.out.println("Dataset partitioned successfully.");
      System.exit(0);
    } else {
      System.err.println("Error while partitioning dataset.");
      System.exit(3);
    }
  }
}