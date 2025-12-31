(ns ktra-indexer.parser
  "Tracklist parser"
  (:refer-clojure :exclude [range iterate format max min])
  (:require [clojure.string :as str]
            [java-time.api :as jt]
            [ktra-indexer.db :as db])
  (:import (java.time DayOfWeek LocalDateTime)
           (org.jsoup Connection Jsoup)
           org.jsoup.select.Elements
           (org.jsoup.nodes Attributes Document Element)))

(defn get-friday-date
  "Returns the date of the same week's Friday formatted as dd.mm.yyyy.
  Input date must be in ISO 8601 date-time offset format."
  [date]
  (let [parsed-date (jt/local-date-time (jt/formatter :iso-offset-date-time)
                                        date)
        friday (jt/local-date (LocalDateTime/.plusDays
                               parsed-date
                               (- (DayOfWeek/.getValue
                                   DayOfWeek/FRIDAY)
                                  (DayOfWeek/.getValue
                                   (LocalDateTime/.getDayOfWeek
                                    parsed-date)))))]
    friday))

(defn get-episode-info
  "Parses the tracklist from SoundCloud and return date, title, and tracklist
  in a map. The SoundCloud URL must be valid, no input validation is performed
  on it."
  [db-con sc-url]
  (let [document (Connection/.get (Jsoup/connect sc-url))
        ;; Remove description from tracklist start
        tracklist (str/join "\n" (str/split-lines
                                  (Attributes/.get (Element/.attributes
                                                    (Elements/.first
                                                     (Element/.select
                                                      document
                                                      "article > p > meta")))
                                                   "content")))
        title (str/trim (str/replace (first (str/split (Document/.title
                                                        document)
                                                       #" by"))
                                     "Stream" ""))
        title-match (re-matches #"(.+) \(KTRA .+ (\d+)\)" title)
        max-date (db/get-episode-max-date db-con)
        cand-date (when max-date (jt/plus max-date (jt/weeks 1)))
        sc-friday-date (get-friday-date (Element/.text (first (Element/.select
                                                               document "time"))))
        friday-date (if-not cand-date
                      sc-friday-date
                      (if (jt/> cand-date sc-friday-date)
                        cand-date sc-friday-date))]
    {:title (if-not title-match
              title
              (str "Episode " (nth title-match 2) ": " (nth title-match 1)))
     :tracklist (if (str/index-of (str/lower-case tracklist) "tracklist")
                  (str/triml (subs tracklist (+ (str/index-of tracklist
                                                              "Tracklist")
                                                (count "Tracklist"))))
                  "")
     :date (jt/format :iso-local-date friday-date)}))
