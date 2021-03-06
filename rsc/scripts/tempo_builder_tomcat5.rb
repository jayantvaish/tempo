#!/usr/bin/env ruby

# loading repositories, dependencies files to locate artifacts
@@script_folder = File.dirname(File.expand_path("#{$0}"))
load "#{@@script_folder}/../scripts/tempo_builder_lib.rb"
load "#{@@script_folder}/../scripts/config.rb"

BASE_PACKAGE = "org.intalio.tomcat"
VERSION_NUMBER = '6.0.4.011'

BUILD_CONFIG = {
  :directory => "./target",
  :mode => [BuildMode::BPMS,BuildMode::UIFW, BuildMode::CAS, BuildMode::LDAP],#BuildMode::RELEASE],
  # :mode => [BuildMode::JETTY, BuildMode::UIFW, BuildMode::CAS, BuildMode::LDAP],
  :ode => :v1_3_4_snapshot,
  :tomcat => :v5,
  :jetty => :v7,
  :osgi_jetty => :v7,
  :artifact => "org.intalio.tomcat:tempo-tomcat5:zip:#{VERSION_NUMBER}",
  :tempo => {
    :core => "6.0.4.011",
    :security => "1.0.16",
    :deploy => "1.0.29",

    :processes => "6.0.12",
    :formManager => "6.0.0.48",
    :apacheds => "6.0.0.37",
    :cas => "6.0.0.37"
  }
  
}

tb = TempoBuilder.new
tb.build BUILD_CONFIG