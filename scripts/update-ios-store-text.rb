#!/usr/bin/env ruby
# Pushes the App Store listing texts in iosApp/fastlane/metadata (description, keywords,
# promotional text, name, subtitle and the App Review notes) to the app's editable App Store
# version. It sets those fields one by one through the App Store Connect API instead of running
# `fastlane metadata`, whose deliver upload also deletes the App Review attachment.
#
#   ASC_KEY_ID=… ASC_ISSUER_ID=… ASC_KEY_P8="$(cat AuthKey.p8)" scripts/update-ios-store-text.rb
#
# URLs, categories and copyright are left as App Store Connect has them.
require "base64"
require "json"
require "net/http"
require "openssl"

ROOT = File.expand_path("..", __dir__)
APP_ID = "6819282127"
METADATA = File.join(ROOT, "iosApp/fastlane/metadata")

def token
  key = OpenSSL::PKey::EC.new(ENV.fetch("ASC_KEY_P8"))
  b64 = ->(s) { Base64.urlsafe_encode64(s).delete("=") }
  now = Time.now.to_i
  header = { alg: "ES256", kid: ENV.fetch("ASC_KEY_ID"), typ: "JWT" }
  claims = { iss: ENV.fetch("ASC_ISSUER_ID"), iat: now, exp: now + 900, aud: "appstoreconnect-v1" }
  input = "#{b64.(header.to_json)}.#{b64.(claims.to_json)}"
  # ES256 wants the raw 64-byte r||s signature, not OpenSSL's DER encoding.
  r, s = OpenSSL::ASN1.decode(key.dsa_sign_asn1(OpenSSL::Digest::SHA256.digest(input))).value.map { |v| v.value.to_s(2).rjust(32, "\0") }
  "#{input}.#{b64.(r + s)}"
end

def api(method, path, body = nil)
  uri = URI("https://api.appstoreconnect.apple.com#{path}")
  request = Net::HTTP.const_get(method.capitalize).new(uri)
  request["Authorization"] = "Bearer #{token}"
  if body
    request["Content-Type"] = "application/json"
    request.body = body.to_json
  end
  response = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |http| http.request(request) }
  data = response.body.to_s.empty? ? {} : JSON.parse(response.body)
  raise "#{method.upcase} #{path}: HTTP #{response.code} #{data['errors']&.map { |e| e['detail'] }}" unless response.code.start_with?("2")
  data
end

def text(locale, name)
  File.read(File.join(METADATA, locale, "#{name}.txt")).strip
end

def update(type, id, attributes)
  api(:patch, "/v1/#{type}/#{id}", { data: { type: type, id: id, attributes: attributes } })
end

version = api(:get, "/v1/apps/#{APP_ID}/appStoreVersions?filter[appStoreState]=PREPARE_FOR_SUBMISSION,DEVELOPER_REJECTED,REJECTED,METADATA_REJECTED")["data"].first
abort "No editable App Store version" unless version
puts "Version #{version['attributes']['versionString']}:"

api(:get, "/v1/appStoreVersions/#{version['id']}/appStoreVersionLocalizations")["data"].each do |localization|
  locale = localization["attributes"]["locale"]
  update("appStoreVersionLocalizations", localization["id"],
         description: text(locale, "description"), keywords: text(locale, "keywords"), promotionalText: text(locale, "promotional_text"))
  puts "  #{locale}: description, keywords, promotional text"
end

# Name and subtitle belong to the app info that goes out with the next version, not the live one.
info = api(:get, "/v1/apps/#{APP_ID}/appInfos")["data"].find { |i| i["attributes"]["state"] != "READY_FOR_DISTRIBUTION" }
abort "No editable app info" unless info
api(:get, "/v1/appInfos/#{info['id']}/appInfoLocalizations")["data"].each do |localization|
  locale = localization["attributes"]["locale"]
  update("appInfoLocalizations", localization["id"], name: text(locale, "name"), subtitle: text(locale, "subtitle"))
  puts "  #{locale}: name, subtitle"
end

detail = api(:get, "/v1/appStoreVersions/#{version['id']}/appStoreReviewDetail")["data"]
update("appStoreReviewDetails", detail["id"], notes: File.read(File.join(METADATA, "review_information/notes.txt")).strip)
puts "  App Review notes"
