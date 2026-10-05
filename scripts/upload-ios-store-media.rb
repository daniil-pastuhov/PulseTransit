#!/usr/bin/env ruby
# Uploads the App Store screenshots and app previews in iosApp/fastlane/{screenshots,app_previews}
# to the app's editable App Store version, replacing what is there. fastlane deliver handles
# screenshots but not previews, so this talks to the App Store Connect API directly.
#
#   ASC_KEY_ID=… ASC_ISSUER_ID=… ASC_KEY_P8="$(cat AuthKey.p8)" scripts/upload-ios-store-media.rb
#
# Files are named <device>_<n>_<scene>.jpg and <device>_preview.mp4, device being iphone (6.9")
# or ipad (13"), in one folder per App Store locale.
require "base64"
require "digest"
require "json"
require "net/http"
require "openssl"

ROOT = File.expand_path("..", __dir__)
APP_ID = "6819282127"
SCREENSHOT_TYPES = { "iphone" => "APP_IPHONE_67", "ipad" => "APP_IPAD_PRO_3GEN_129" }.freeze
PREVIEW_TYPES = { "iphone" => "IPHONE_67", "ipad" => "IPAD_PRO_3GEN_129" }.freeze

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

# Sends the file in the parts App Store Connect asked for, then marks it uploaded.
def upload(kind, set_relationship, set_id, file)
  bytes = File.binread(file)
  attributes = { fileName: File.basename(file), fileSize: bytes.bytesize }
  attributes[:mimeType] = "video/mp4" if kind == "appPreviews"
  created = api(:post, "/v1/#{kind}", { data: { type: kind, attributes: attributes,
    relationships: { set_relationship => { data: { type: "#{set_relationship}s", id: set_id } } } } })["data"]
  created["attributes"]["uploadOperations"].each do |operation|
    uri = URI(operation["url"])
    request = Net::HTTP.const_get(operation["method"].capitalize).new(uri)
    operation["requestHeaders"].each { |h| request[h["name"]] = h["value"] }
    request.body = bytes.byteslice(operation["offset"], operation["length"])
    response = Net::HTTP.start(uri.host, uri.port, use_ssl: true) { |http| http.request(request) }
    raise "upload of #{file} failed: HTTP #{response.code}" unless response.code.start_with?("2")
  end
  api(:patch, "/v1/#{kind}/#{created['id']}", { data: { type: kind, id: created["id"],
    attributes: { uploaded: true, sourceFileChecksum: Digest::MD5.hexdigest(bytes) } } })
  puts "  uploaded #{File.basename(file)}"
end

# The set for one display type, created if missing and emptied if not.
def fresh_set(localization_id, sets_path, set_type, type_attribute, type_value, items_path)
  existing = api(:get, "/v1/appStoreVersionLocalizations/#{localization_id}/#{sets_path}")["data"]
    .find { |set| set["attributes"][type_attribute] == type_value }
  if existing
    api(:get, "/v1/#{set_type}/#{existing['id']}/#{items_path}")["data"].each { |item| api(:delete, "/v1/#{items_path}/#{item['id']}") }
    return existing["id"]
  end
  api(:post, "/v1/#{set_type}", { data: { type: set_type, attributes: { type_attribute => type_value },
    relationships: { appStoreVersionLocalization: { data: { type: "appStoreVersionLocalizations", id: localization_id } } } } })["data"]["id"]
end

version = api(:get, "/v1/apps/#{APP_ID}/appStoreVersions?filter[appStoreState]=PREPARE_FOR_SUBMISSION,DEVELOPER_REJECTED,REJECTED,METADATA_REJECTED")["data"].first
abort "No editable App Store version" unless version
localizations = api(:get, "/v1/appStoreVersions/#{version['id']}/appStoreVersionLocalizations")["data"]
  .to_h { |l| [l["attributes"]["locale"], l["id"]] }

localizations.each do |locale, localization_id|
  puts "#{locale} (version #{version['attributes']['versionString']}):"
  SCREENSHOT_TYPES.each do |device, display_type|
    files = Dir[File.join(ROOT, "iosApp/fastlane/screenshots", locale, "#{device}_*.{jpg,png}")].sort
    next if files.empty?
    set_id = fresh_set(localization_id, "appScreenshotSets", "appScreenshotSets", "screenshotDisplayType", display_type, "appScreenshots")
    files.each { |file| upload("appScreenshots", :appScreenshotSet, set_id, file) }
  end
  PREVIEW_TYPES.each do |device, preview_type|
    files = Dir[File.join(ROOT, "iosApp/fastlane/app_previews", locale, "#{device}_*.mp4")].sort
    next if files.empty?
    set_id = fresh_set(localization_id, "appPreviewSets", "appPreviewSets", "previewType", preview_type, "appPreviews")
    files.each { |file| upload("appPreviews", :appPreviewSet, set_id, file) }
  end
end
