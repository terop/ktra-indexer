(ns ktra-indexer.parser
  "Tracklist parser"
  (:refer-clojure :exclude [range iterate format max min])
  (:require [clojure.string :as str]
            [java-time.api :as t])
  (:import (java.time DayOfWeek LocalDateTime)
           (org.jsoup Connection Jsoup)
           org.jsoup.select.Elements
           (org.jsoup.nodes Attributes Document Element)))

(defn get-friday-date
  "Returns the date of the same week's Friday formatted as dd.mm.yyyy.
  Input date must be in ISO 8601 date-time offset format."
  [date]
  (let [parsed-date (t/local-date-time (t/formatter :iso-offset-date-time)
                                       date)
        friday (t/local-date (LocalDateTime/.plusDays
                              parsed-date
                              (- (DayOfWeek/.getValue
                                  DayOfWeek/FRIDAY)
                                 (DayOfWeek/.getValue
                                  (LocalDateTime/.getDayOfWeek
                                   parsed-date)))))]
    (t/format "y-MM-dd" friday)))

(defn parse-sc-tracklist
  "Parse tracklist from SoundCloud and return date, title, and tracklist
  in a map. The SoundCloud URL must be valid, no input validation is performed
  on it."
  [sc-url]
  (let [document (Connection/.get (Jsoup/connect sc-url))
        ;; Remove description from tracklist start
        tracklist (str/join "\n" (str/split-lines
                                  (Attributes/.get (Element/.attributes
                                                    (Elements/.first
                                                     (Element/.select
                                                      document
                                                      "article > p > meta")))
                                                   "content")))]
    {:title (str/trim (str/replace (first (str/split (Document/.title document)
                                                     #" by"))
                                   "Stream" ""))
     :tracklist (if (str/index-of (str/lower-case tracklist) "tracklist")
                  (str/triml (subs tracklist (+ (str/index-of tracklist
                                                              "Tracklist")
                                                (count "Tracklist"))))
                  tracklist)
     :date (get-friday-date (Element/.text (first (Element/.select
                                                   document "time"))))}))
