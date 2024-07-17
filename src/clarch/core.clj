(ns clarch.core
  (:require [clojure.java.io :as io])
  (:import [java.io
            ByteArrayInputStream ByteArrayOutputStream
            BufferedInputStream BufferedOutputStream]
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

(defn read-zip-entry! ^"[B" [^ZipFile zf ^String entry-name]
  (let [ze (.getEntry zf entry-name)
        baos (ByteArrayOutputStream.)]
    (when ze
      (io/copy (.getInputStream zf ze) baos)
      (.toByteArray ^ByteArrayOutputStream baos))))

(defn read-first-zip-entry! ^"[B" [^String zip-file]
  (let [zf (ZipFile. zip-file)
        bytes (read-zip-entry!
               zf
               (.getName ^ZipArchiveEntry (.nextElement (.getEntries zf))))]
    (.close zf)
    bytes))

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

#_
(with-open [zos (zip-output-stream "filename.zip")]
  (write-zip-entry! zos (.getBytes "bytes-to-write") "zip-entry-name.txt")
  (.finish zos))
