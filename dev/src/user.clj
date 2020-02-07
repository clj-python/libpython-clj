(ns user)

;; debug print with #p
(require 'hashp.core)

(def dev-mode true)
(println "***************************")
(println {:msg "dev mode" :status (if (true? dev-mode) "on" "off")})
(println "***************************")
