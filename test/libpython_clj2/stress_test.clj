(ns libpython-clj2.stress-test
  "A set of tests meant to crash the system or just run the system out of
  memory if it isn't setup correctly."
  (:require [libpython-clj2.python :as py]
            [libpython-clj2.python.ffi :as py-ffi]
            [libpython-clj2.python.fn :as py-fn]
            [libpython-clj2.python.class :as py-class]
            [clojure.test :refer :all]
            [clojure.edn :as edn]))


(py/initialize!)


(def test-script
  (memoize
   (fn []
     (->  (py/run-simple-string "
import itertools

def getdata():
    while True:
        yield {'a': 1, 'b': 2}

def print_data():
    print(\"hey\")

def getmultidata():
    a =  {'disableReason': 'None', 'generationNumber': 1186, 'leDomain': 0, 'protocol': 'FC', 'index': 197, 'authentication': 'None', 'physicalID': 4, 'cFlags': ['0x1'], 'wwn': '20:c5:00:05:1e:ba:a7:00', 'scn': 'Offline', 'health': 'OFFLINE', 'connectionSpeed': 'N8Gbps', 'shareIFID': None, 'id': '586a6086ce8d6e5d86a0a04b', 'flags': ['0x4001', 'PRESENT', 'LED'], 'portID': '01c500', 'storagecenterSnapshotType': 'live', 'transitionCount': 1, 'portType': 17.0, 'podPort': None, 'name': 'slot9 port21', 'deviceNumber': 267654, 'isPartOtherAD': False, 'creditRecovery': 'Inactive', 'distance': 'normal', 'storagecenterSnapshotTime': '2019-11-04T14:12:20.740000+00:00', 'scnID': 2, 'portIFID': '4392001d', 'fcFastwrite': False, 'state': 'Offline', 'wwnConnected': [], 'faa': 'Inactive', 'localSwcFlags': ['0x0'], 'stateID': 2, 'aoq': 'Inactive', 'physical': 'No_Light', 'peerBeacon': False}
    b =   {'disableReason': 'None', 'generationNumber': 0, 'leDomain': 0, 'protocol': 'FC', 'index': 777, 'authentication': 'None', 'physicalID': 6, 'cFlags': ['0x1'], 'wwn': '50:00:53:32:3d:6f:73:09', 'scn': 'Online', 'health': 'HEALTHY', 'connectionSpeed': 'N8Gbps', 'shareIFID': None, 'id': '586a603560a298494d6a5e97', 'flags': ['0x24b03', 'PRESENT', 'ACTIVE', 'F_PORT', 'G_PORT', 'LOGICAL_ONLINE', 'LOGIN', 'NOELP', 'LED', 'ACCEPT'], 'portID': '3d8d80', 'storagecenterSnapshotType': 'deleted', 'transitionCount': 0, 'portType': 17.0, 'podPort': None, 'name': 'slot1 port57', 'deviceNumber': 365521, 'isPartOtherAD': None, 'creditRecovery': 'Inactive', 'distance': 'normal', 'storagecenterSnapshotTime': '2018-06-07T02:12:16.083000+00:00', 'scnID': 1, 'portIFID': '43120039', 'fcFastwrite': False, 'state': 'Online', 'wwnConnected': ['21:00:00:24:ff:f7:37:01'], 'faa': 'Inactive', 'localSwcFlags': ['0x0'], 'stateID': 1, 'aoq': 'Inactive', 'physical': 'In_Sync', 'peerBeacon': False}
    c =  {'disableReason': 'None', 'generationNumber': 0, 'leDomain': 0, 'protocol': 'FC', 'index': 127, 'authentication': 'None', 'physicalID': 4, 'cFlags': ['0x1'], 'wwn': '20:7f:00:05:33:61:48:01', 'scn': 'Offline', 'health': 'OFFLINE', 'connectionSpeed': 'N8Gbps', 'shareIFID': None, 'id': '5c7d9d73c146f2bb9dd1a48c', 'flags': ['0x1', 'PRESENT'], 'portID': '42e000', 'storagecenterSnapshotType': 'live', 'transitionCount': 1, 'portType': 17.0, 'podPort': None, 'name': 'slot12 port15', 'deviceNumber': 382664, 'isPartOtherAD': None, 'creditRecovery': 'Inactive', 'distance': 'normal', 'storagecenterSnapshotTime': '2019-03-04T21:49:38.234000+00:00', 'scnID': 2, 'portIFID': '43c20037', 'fcFastwrite': False, 'state': 'Offline', 'wwnConnected': [], 'faa': None, 'localSwcFlags': ['0x0'], 'stateID': 2, 'aoq': None, 'physical': 'No_Light', 'peerBeacon': False}
    d =  {'disableReason': 'None', 'generationNumber': 506, 'leDomain': 0, 'protocol': 'FC', 'index': 187, 'authentication': 'None', 'physicalID': 4, 'cFlags': ['0x1'], 'wwn': '20:bb:00:05:33:24:6a:01', 'scn': 'Offline', 'health': 'OFFLINE', 'connectionSpeed': 'N8Gbps', 'shareIFID': None, 'id': '586a604a1d2c89767dc345f1', 'flags': ['0x4001', 'PRESENT', 'LED'], 'portID': '3ed100', 'storagecenterSnapshotType': 'live', 'transitionCount': 1, 'portType': 17.0, 'podPort': None, 'name': 'slot4 port27 DISABLED server 640461 seg config', 'deviceNumber': 365522, 'isPartOtherAD': None, 'creditRecovery': 'Inactive', 'distance': 'normal', 'storagecenterSnapshotTime': '2019-10-18T13:10:24.968000+00:00', 'scnID': 2, 'portIFID': '4342100b', 'fcFastwrite': False, 'state': 'Offline', 'wwnConnected': [], 'faa': 'Inactive', 'localSwcFlags': ['0x0'], 'stateID': 2, 'aoq': 'Inactive', 'physical': 'No_Light', 'peerBeacon': False}
    for x in itertools.cycle([a, b, c, d]):
        yield x
")
          :globals))))


(defn get-data
  []
  (let [gd-fn (-> (test-script)
                  (get "getdata"))]
    (gd-fn)))


(defn print-data
  []
  (let [pr-fn (-> (test-script)
                  (get "print_data"))]
    (pr-fn)))


(defn get-multi-data
  []
  (let [gd-fn (-> (test-script)
                  (get "getmultidata"))]
    (gd-fn)))


(deftest forever-test
  (doseq [items (take 10 (partition 999 (get-data)))]
    ;;One way is to use the GC
    (time
     (do
       (last
        (eduction
         (map py/->jvm)
         (map (partial into {}))
         items))
       (System/gc)))
    ;;A faster way is to grab the gil and use the resource system
    ;;This also ensures that resources within that block do not escape
    (time
     (py/with-gil-stack-rc-context
       (last
        (eduction
         (map py/->jvm)
         (map (partial into {}))
         items))))))


(deftest multidata-test
  (doseq [items (take 5 (partition 1000 (get-multi-data)))]
    (time (do (py/with-gil-stack-rc-context
                (mapv py/->jvm items))
              :ok))))


(deftest str-marshal-test
  (let [test-str (py/->python "a nice string to work with")]
    (time
     (py/with-gil-stack-rc-context
       (dotimes [iter 1000]
         (py/->jvm test-str))))))


(deftest dict-marshal-test
  (let [test-item (py/->python {:a 1 :b 2})]
    (time
     (py/with-gil-stack-rc-context
       (dotimes [iter 100]
         (py/->jvm test-item))))))


(deftest print-stress-test
  (dotimes [iter 100]
    (with-out-str
      (print-data))))


(deftest new-cls-stress-test
  (dotimes [iter 100]
    (py/with-gil-stack-rc-context
      (let [test-cls (py-class/create-class
                      "testcls" nil
                      {"__init__" (py-class/make-tuple-instance-fn
                                   (fn [self name shares price]
                                     (py/set-attr! self "name" name)
                                     (py/set-attr! self "shares" shares)
                                     (py/set-attr! self "price" price)
                                     nil))
                       "cost" (py-class/make-tuple-instance-fn
                               (fn [self]
                                 (let [self (py/as-jvm self)]
                                   (* (py/py.- self shares)
                                      (py/py.- self price)))))
                       "__str__" (py-class/make-tuple-instance-fn
                                  (fn [self]
                                    (let [self (py/as-jvm self)]
                                      ;;Self is just a dict so it converts to a hashmap
                                      (pr-str {"name" (py/py.- self name)
                                               "shares" (py/py.- self shares)
                                               "price" (py/py.- self price)}))))
                       "testvar" 55})
            new-inst (test-cls "ACME" 50 90)]
        (is (= 4500
               (py/$a new-inst cost)))
        (is (= 55 (py/py.- new-inst testvar)))

        (is (= {"name" "ACME", "shares" 50, "price" 90}
               (edn/read-string (.toString new-inst))))))))


(deftest fastcall
  (let [gilstate (py-ffi/lock-gil)]
    (try
      (let [test-fn (-> (py/run-simple-string "def calcSpread(bid,ask):\n\treturn bid-ask\n\n")
                        :globals
                        (get "calcSpread"))
            call-ctx (py-fn/allocate-fastcall-context)
            n-calls 100000
            start-ns (System/nanoTime)
            _ (is (= -1  (py-fn/fastcall call-ctx test-fn 1 2)))
            _ (dotimes [iter n-calls]
                (py-fn/fastcall call-ctx test-fn 1 2))
            end-ns (System/nanoTime)
            ms (/ (- end-ns start-ns) 10e6)]
        (py-fn/release-fastcall-context call-ctx)
        (println "Python fn calls/ms" (/ n-calls ms)))
      (finally
        (py-ffi/unlock-gil gilstate)))))
