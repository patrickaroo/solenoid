(ns solenoid.components
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [solenoid.utils :as u]
            [solenoid.controls :as c])
  (:import [solenoid.controls Slider Num Text EdnBlock]))

(defn action-button
  [{:keys [id text]}]
  [:button.btn-close
   {:hx-post (str "/action/" id)
    :hx-swap "none"}
   (or text id)])

;; PROBLEM: make the components take in the exact shape of the record, nothing more
;; PROBLEM: make the components return the exact shape of the record, with updated value
;;          do this by having some to/from json fns that jsonify the control record coming in,
;;          and a fn that properly builds the hx-vals 'map'
;; PROBLEM: de-dupe the structure of the controls renderers a bit (eg. controls-key/id/class controls-val-key/id/class etc.)


(defn- make-hx-vals
  [m get-value-js-str]
  (-> (str "js:" (json/generate-string (assoc m :value "____")))
      (str/replace #"\"____\"" get-value-js-str)))

;; PROBLEM: make a mechanism that easily swaps :input :textearea, etc. to make the base-component more re-usable
(def control-type->input-type
  {:slider "range"
   :num    "number"
   :text   "text"
   :edn    "textarea"})

(defn- make-input-map
  [{:keys [id value control-type]}]
  {:id         id
   :class      ["form-control-sm" (name control-type) "col"]
   :type       (control-type->input-type control-type)
   :value      (str value)
   :hx-get     (str "/controller/" id)
   :hx-trigger "input"
   :hx-target  (str "#" (name id) "-value")})

(defn base-component
  ([control] (base-component control {} {}))
  ([{:keys [id display-name value min max control-type] :as control}
    input-map-overrides value-container-map-overrides]
   (let [get-value-js-str (str "document.getElementById('" (name id) "').value")]
     [:div.control.row.my-1.mx-0.p-0
      [:span.col-3.text-end.mb-0.mt-2 (str (or display-name id))]
      [:span.col-7 {:class (str (name control-type) "-container")}
       [:div.row.small
        (when min [:span.col-2.text-end.mb-0.mt-1 min])
        [:input
         (merge
           (make-input-map control)
           {:hx-vals (make-hx-vals control get-value-js-str)}
           control
           input-map-overrides)]
        (when max [:span.col-2.mb-0.mt-1 max])]]
      [:span.col-2.mb-0.mt-1
       (merge
         {:id    (str (name id) "-value")
          :style {:display "none"}}
         value-container-map-overrides) (str value)]])))

(defmulti render-controller type)
(defmethod render-controller :default [m] (base-component m))
(defmethod render-controller Num      [m] (base-component m))
(defmethod render-controller Text     [m] (base-component m))
(defmethod render-controller Slider   [m] (base-component m {} {:style {:display "inline-block"}}))
(defmethod render-controller EdnBlock [m] (let [val (str (:value m))] (base-component (assoc m :value val))))

(defmulti render-control-block-result (fn [control-block _] (-> control-block :state deref meta :result-type)))
(defmulti render-control-block (fn [control-block] (-> control-block :state deref meta :control-block-type)))

(defmethod render-control-block-result :default
  [{:keys [id state]} oob?]
  (let [result @state]
    [:div.text-center
     (merge
       {:id          (str (name id) "-result")
        :class       "control-block-result"}
       (when oob? {:hx-swap-oob "innerHTML"}))
     (or result "no result")]))

(defn render-control-block*
  "Base implementation for control block render methods, useful for creating custom `render-control-block` methods."
  [{:keys [id control-ids]} rendered-result]
  (let [controls (map @c/registry control-ids)]
    [:div.col.g-4
     [:div.card {:id id}
      [:div.card-header
       [:span.row
        [:h5.col.card-title.mb-0.mt-1 id]
        [:button.btn-close.text-end.col-1.px-2
         {:hx-post (str "/action/delete/" id)
          :hx-swap "none"}]]]
      [:div.card-body
       (into [:div.row.controls] (mapv render-controller controls))
       [:div.row.my-3 rendered-result]
       [:div.row
        [:div.col [:button.btn.btn-outline-secondary.btn-sm
                   {:hx-post (str "/action/def/" id)
                    :hx-swap "none"} "def"]]]]]]))

(defmethod render-control-block :default
  [control-block]
  (render-control-block*
    control-block
    (render-control-block-result control-block false)))

#_(defn toggle
  [{:keys [id value] :as val-map}]
  [:div
   [:span (str id "  ")]
   [:span.togglecontainer
    [(keyword (str "input#" (name id) ".toggle"))
     (merge
       {:type      "toggle"
        :checked   (u/maybe-parse-boolean value)
        :hx-get    (str "/controller/" id)
        :hx-target (str "#" (name id) "-value")
        :hx-vals   (str "js:{"
                        "value: document.getElementById('" (name id) "').checked, "
                        "type: 'toggle', " "}")}
       val-map)]
    [(keyword (str "div#" (name id) "-value"))
     {:style {:display "none"}}
     value]]])


#_(defn dropdown
  [{:keys [id value options] :as val-map}]
  (let [select [(keyword (str "select#" (name id) ".dropdown"))
                (merge
                  {:hx-get     (str "/controller/" id)
                   :hx-target  (str "#" (name id) "-value")
                   :hx-vals    (str "js:{"
                                    "value: document.getElementById('" (name id) "').value, "
                           "type: 'dropdown', " "}")}
                  val-map)]]
    [:div
     [:span (str id "  ")]
     [:span.dropdowncontainer
      (into select (for [option options]
                     [:option.dropdown-option {:value option} option]))
      [(keyword (str "div#" (name id) "-value"))
       {:style {:display     "none"}}
       value]]]))

#_(defn radio
  [{:keys [id value options] :as val-map}]
  [:div
   [:span (str id "  ")]
   [:span.radiocontainer
    (into [:span]
          (for [[idx option] (map vector (range) options)]
            (let [option-id (str (name id) "-" idx)]
              [:span
               [(keyword (str "input#" option-id ".radio-option"))
                {:type      "radio"
                 :name      id
                 :value     option
                 :hx-get    (str "/controller/" id)
                 :hx-target (str "#" (name id) "-value")
                 :hx-vals   (str "js:{"
                                 "value: document.getElementById('" option-id "').value, "
                                 "type: 'radio', " "}")}]
               [:label {:for option-id} option]])))
    [(keyword (str "div#" (name id) "-value"))
     {:style {:display "none"}}
     value]]])

#_(defn checkbox
  [{:keys [id value] :as val-map}]
  [:div
   [:span (str id "  ")]
   [:span.checkboxcontainer
    (into [:span]
          (for [[idx [option v]] (map vector (range) value)]
            (let [option-id (str (name id) "-" idx)
                  new-value (-> value
                                (assoc option (str "document.getElementById('" option-id "').checked, "))
                                (update-keys #(str (name %) ":")))]
              [:span
               [(keyword (str "input#" option-id ".checkbox-option"))
                {:type      "checkbox"
                 :name      id
                 :value     option
                 :checked   v
                 :hx-get    (str "/controller/" id)
                 :hx-target (str "#" (name id) "-value")
                 :hx-vals   (str "js:{"
                                 "value: document.getElementById('" option-id "').checked, "
                                 "option: " option-id ", "
                                 "type: 'checkbox', " "}")}]
               [:label {:for option-id} option]])))
    [(keyword (str "div#" (name id) "-value"))
     {:style {:display "none"}}
     value]]])
