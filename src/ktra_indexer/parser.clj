(ns ktra-indexer.parser
  "Tracklist parser"
  (:refer-clojure :exclude [range iterate format max min])
  (:require [clojure.string :as s]
            [java-time.api :as t])
  (:import java.time.DayOfWeek
           org.jsoup.Jsoup
           org.jsoup.nodes.Element))

(defn get-friday-date
  "Returns the date of the same week's Friday formatted as dd.mm.yyyy.
  Input date must be in ISO 8601 date-time offset format."
  [date]
  (let [parsed-date (t/local-date-time (t/formatter :iso-offset-date-time)
                                       date)
        friday (t/local-date (.plusDays parsed-date
                                        (- (.getValue (DayOfWeek/FRIDAY))
                                           (.getValue (.getDayOfWeek
                                                       parsed-date)))))]
    (t/format "y-MM-dd" friday)))

(defn parse-sc-tracklist
  "Parse tracklist from SoundCloud and return date, title, and tracklist
  in a map. The SoundCloud URL must be valid, no input validation is performed
  on it."
  [sc-url]
  (let [document (.get (Jsoup/connect sc-url))
        ;; Remove description from tracklist start
        tracklist (s/join "\n" (s/split-lines
                                (.get (.attributes
                                       (.first
                                        (.select document
                                                 "article > p > meta")))
                                      "content")))]
    {:title (s/trim (s/replace (first (s/split (.title document) #" by"))
                               "Stream" ""))
     :tracklist (if (s/index-of (s/lower-case tracklist) "tracklist")
                  (s/triml (subs tracklist (+ (s/index-of tracklist "Tracklist")
                                              (count "Tracklist"))))
                  tracklist)
     :date (get-friday-date (.text ^Element (first
                                             (.select document "time"))))}))
