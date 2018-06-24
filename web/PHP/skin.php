<?php
/**
 * Simple Skin Generator
 *
 * @author DrJest
 * @link https://github.com/DrJest/PlayerLocations
 * @see http://wiki.vg/Mojang_API
 */

  header('Pragma: public');
  header('Cache-Control: max-age=86400');
  header('Expires: '. gmdate('D, d M Y H:i:s \G\M\T', time() + 86400));
  header('Content-Type: image/png');

  require_once('MojangAPI.class.php');

  if(isset($_GET['size'])) {
    $size = intval($_GET['size']);
  }
  else {
    $size = 100;
  }

  if(isset($_GET['uuid'])) {
    $uuid = $_GET['uuid'];
  }
  else if(isset($_GET['name'])) {
    $uuid = MojangAPI::getUuid($_GET['name']);
  }

  $imgData = MojangAPI::getPlayerHead($uuid, $size);

  if(!$imgData) {
    $imgData = MojangAPI::getSteveHead($size);
  }

  $im = imagecreatefromstring($imgData);

  header('Content-Type: image/png');
  imagepng($im);
  imagedestroy($im);
?>