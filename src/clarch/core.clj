(ns clarch.core
  (:require [clojure.java.io :as io])
  (:import [java.util Enumeration]
           [java.io
            ByteArrayInputStream ByteArrayOutputStream
            BufferedInputStream BufferedOutputStream
            File RandomAccessFile
            SequenceInputStream]
           [java.util.zip InflaterInputStream DeflaterInputStream
            Inflater InflaterInputStream]
           [org.apache.commons.compress.archivers.zip
            ZipArchiveEntry ZipArchiveOutputStream ZipFile]
           [org.apache.commons.compress.archivers.tar TarFile
            TarArchiveEntry TarArchiveOutputStream TarArchiveInputStream]
           [org.apache.commons.compress.compressors
            CompressorInputStream CompressorStreamFactory]
           [org.apache.commons.compress.compressors.gzip
            GzipCompressorOutputStream GzipCompressorInputStream]
           [org.apache.commons.compress.utils IOUtils]))

(defn compressor-input-stream ^CompressorInputStream [filename]
  (let [in ^BufferedInputStream (io/input-stream filename)]
    (.createCompressorInputStream (CompressorStreamFactory.) in)))

(defn targz-input-stream ^TarArchiveInputStream [filename]
  (->> filename
       io/input-stream
       (GzipCompressorInputStream.)
       (TarArchiveInputStream.)))

(defn zip-output-stream ^ZipArchiveOutputStream [filename]
  (let [out ^BufferedOutputStream (io/output-stream filename)]
    (ZipArchiveOutputStream. out)))

(defn tar-output-stream "Creates an uncompressed tar ball"
  ^TarArchiveOutputStream [filename]
  (let [out ^BufferedOutputStream (io/output-stream filename)]
    (TarArchiveOutputStream. out)))

(defn targz-output-stream
  "Creates a compressed TAR ball"
  ^TarArchiveOutputStream [filename]
  (let [bos ^BufferedOutputStream (io/output-stream filename)
        gzo (GzipCompressorOutputStream. bos)]
    (TarArchiveOutputStream. gzo)))

(defn write-zip-entry!
  [^ZipArchiveOutputStream zip-os ^"[B" bytes ^String entry-name]
  (let [ze (ZipArchiveEntry. entry-name)]
    (.setSize ze (count bytes))
    (with-open [is ^ByteArrayInputStream (ByteArrayInputStream. bytes)]
      (.putArchiveEntry zip-os ze)
      (io/copy is zip-os)
      (.closeArchiveEntry zip-os))))

(defn write-tar-entry!
  [^TarArchiveOutputStream tar-os ^"[B" bytes ^String entry-name]
  (let [te (TarArchiveEntry. entry-name)]
    (.setSize te (count bytes))
    (with-open [is ^ByteArrayInputStream (ByteArrayInputStream. bytes)]
      (.putArchiveEntry tar-os te)
      (io/copy is tar-os)
      (.closeArchiveEntry tar-os))))

(defn tar-file [^String filename]
  (TarFile. (io/file filename)))

(defn zip-entry->bytes
  [^ZipFile zf ^ZipArchiveEntry entry]
  (let [baos (ByteArrayOutputStream.)]
    (io/copy (.getInputStream zf entry) baos)
    (.toByteArray ^ByteArrayOutputStream baos)))

(defn file-bytes ^"[B"
  [^File zip-file offset len]
  (when (and offset len)
    (let [bytes (byte-array len)]
      (doto (RandomAccessFile. zip-file "r")
        (.seek offset)
        (.read bytes 0 len)
        (.close))
      bytes)))

(def ^"[B" ONE_ZERO_BYTE (byte-array 1))

(defn deflate-bytes ^"[B" [^"[B" raw-bytes]
  (let [inf ^Inflater (Inflater. true)]
    (with-open [bais (ByteArrayInputStream. raw-bytes)
                is (BufferedInputStream. bais)
                sis ^SequenceInputStream (SequenceInputStream.
                                          is (ByteArrayInputStream. ONE_ZERO_BYTE))
                iisws (InflaterInputStream. sis inf)
                baos (ByteArrayOutputStream.)]
      (io/copy iisws baos)
      (.end inf)
      (.toByteArray baos))))

(defn deflated-bytes
  ([^File zip-file offset compressed-size]
   (-> zip-file
       (file-bytes offset compressed-size)
       deflate-bytes))
  ([^File zip-file {:keys [offset compressed-size]}]
   (deflated-bytes zip-file offset compressed-size)))

(defn zip-bytes ^"[B" [^"[B" u-bytes]
  (with-open [in (ByteArrayInputStream. u-bytes)
              din (DeflaterInputStream. in)
              baos (ByteArrayOutputStream.)]
    (io/copy din baos)
    (.toByteArray baos)))

(defn unzip-bytes ^"[B" [^"[B" c-bytes]
  (with-open [in (ByteArrayInputStream. c-bytes)
              iin (InflaterInputStream. in)
              baos (ByteArrayOutputStream.)]
    (io/copy iin baos)
    (.toByteArray baos)))

#_
(defn gz-bytes ^"[B" [^"[B" u-bytes]
  (with-open [in (ByteArrayInputStream. u-bytes)
              baos (ByteArrayOutputStream.)
              gzos (GZIPOutputStream. baos)]
    (io/copy in gzos)
    (.toByteArray baos)))

#_
(defn ungz-bytes ^"[B" [^"[B" c-bytes]
  (with-open [in (ByteArrayInputStream. c-bytes)
              gzin (GZIPInputStream. in)
              baos (ByteArrayOutputStream.)]
    (io/copy gzin baos)
    (.toByteArray baos)))

(defn zip-entries
  "Lazy seq of zip files (auto-skips directories)"
  ([^File zip-file]
   (let [zf (ZipFile. zip-file)]
     (zip-entries zf (.getEntries zf))))
  ([^ZipFile zf ^Enumeration zes]
   (lazy-seq
      (when (.hasMoreElements zes)
        (let [entry ^ZipArchiveEntry (.nextElement zes)]
          (if (.isDirectory entry)
            (zip-entries zf zes)
            (cons entry
                  (zip-entries zf zes))))))))

(defn parsed-zip-entries
  "Lazy seq of parsed zip entries"
  ([^File zip-file]
   (let [zf (ZipFile. zip-file)]
     (parsed-zip-entries zf (.getEntries zf))))
  ([^ZipFile zf zes]
   (map (fn [^ZipArchiveEntry entry]
          {:filename (.getName entry)
           :data (zip-entry->bytes zf entry)})
        (zip-entries zf zes))))

(defn zip-entry-metas
  "Lazy seq of zip entries meta data:
  - :name
  - :offset
  - :compressed-size
  - :method (archive, deflate etc.)"
  ([^File zip-file]
   (let [zf (ZipFile. zip-file)]
     (zip-entry-metas zf (.getEntries zf))))
  ([^ZipFile zf zes]
   (map (fn [^ZipArchiveEntry entry]
          {:filename (.getName entry)
           :offset (.getDataOffset entry)
           :compressed-size (.getCompressedSize entry)
           :method (.getMethod entry)})
        (zip-entries zf zes))))

(defmulti finish-and-close-outputstream! type)

(defmethod finish-and-close-outputstream! ZipArchiveOutputStream
  [^ZipArchiveOutputStream zos]
  (doto zos
    (.finish)
    (.close)))

(defmethod finish-and-close-outputstream! TarArchiveOutputStream
  [^TarArchiveOutputStream zos]
  (doto zos
    (.finish)
    (.close)))

(defn ^"[B" read-current-tar-entry [^TarArchiveInputStream tar-input]
  (let [bais (ByteArrayOutputStream. (.getSize (.getCurrentEntry tar-input)))]
    (IOUtils/copy tar-input bais)
    (.toByteArray bais)))

(defn combine-targz [sources target]
  {:pre [(every? string? sources)
         (string? target)]}
  (with-open [out ^TarArchiveOutputStream (targz-output-stream target)]
    (doseq [^String input sources]
      (with-open [in ^TarArchiveInputStream (targz-input-stream input)]
        (loop [entry ^TarArchiveEntry (.getNextEntry in)]
          (when entry
            (write-tar-entry! out
                              (read-current-tar-entry in)
                              (.getName entry))
            (recur (.getNextEntry in))))))))

(defn targz-entries
  "Return a lazy seq of entries from TarArchiveInputstream.
  Returned as a map with keys :filename and :data (byte array)"
  [^TarArchiveInputStream targz-in]
  (lazy-seq
   (when-let [entry ^TarArchiveEntry (.getNextEntry targz-in)]
     (cons {:filename (.getName entry)
            :data (read-current-tar-entry targz-in)}
           (targz-entries targz-in)))))

(defn targz->zip [targz-filename zip-filename]
  (with-open [in (targz-input-stream targz-filename)
              out (zip-output-stream zip-filename)]
    (doseq [{:keys [data filename]} ^TarArchiveEntry (targz-entries in)]
      (write-zip-entry! out data filename))))

(comment
  (def f (io/file "/path/to/zip-filename"))
  ;; takes a long time, that's why you should parse through all the entries
  ;; sequentially when you first have them!
  (def zems (zip-entry-metas f))
  (->> zems
       (drop 100)
       (take 1)
       (map (partial deflated-bytes f)) ;; DEFLATE is default ZIP compression
       slurp)) ;; parse the deflated bytes however you need!
