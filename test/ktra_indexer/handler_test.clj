(ns ktra-indexer.handler-test
  (:require [clojure.test :refer [deftest is testing]]
            [ring.util.http-response :refer [ok]]
            [terop.openid-connect-auth :refer [access-ok?]]
            [ktra-indexer.handler :as h]))

(deftest wrap-authenticated-test
  (testing "wrap-authenticated function"
    (is (= 302 (:status (h/wrap-authenticated {} identity))))
    (with-redefs [access-ok? (fn [_ _] false)]
      (is (= 302 (:status (h/wrap-authenticated {} identity)))))
    (with-redefs [access-ok? (fn [_ _] true)]
      (is (= 200 (:status (h/wrap-authenticated {}
                                                (fn [_] (ok "Hello")))))))))
