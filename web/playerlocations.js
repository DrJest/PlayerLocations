const pmOptions = {};

///////////////////////////////////////////////////////
///////////// DO NOT EDIT BELOW THIS LINE /////////////
///////////////////////////////////////////////////////

const PlayerLocations = function(settings) {
  let control = null;
  let currentSettings = {};
  let container = null;
  const markersGroup = L.layerGroup();

  const defaultSettings = {
    markersFile: '/players.json',
    refreshRate: 1000,
    skinUrl: '',
    showOfflinePlayers: false,
    followPlayer: null,
  };

  const init = () => {
    if(!overviewer || !overviewer.map) {
      return setTimeout(init, 1000);
    }
    currentSettings = Object.assign({}, defaultSettings, settings);
    control = overviewer.map.addControl(new PlayerMarkerControl());
    markersGroup.addTo(overviewer.map);
    const style = document.createElement('style');
    style.innerHTML = `
      .playerlocations-container {
        padding: 5px;
        background: rgba(255,255,255,1);
        border-radius: 5px;
      }
      .playerlocations-container > select {
        border-radius: 2px;
        display: block;
        width: 100%;
      }
      .playerlocations-container > select > option.offline {
        color: #666;
      }
      .playerlocations-container > label {
        margin: 2px;
        padding: 2px;
      }
    `;
    document.head.appendChild(style);
    updateMarkers();
  }

  const PlayerMarkerControl = L.Control.extend({
    options: {
      position: 'topleft' 
    },
    onAdd: function(map) {
      container = L.DomUtil.create('div', 'leaflet-control leaflet-control-custom playerlocations-container');
      let checkOfflineSelect = L.DomUtil.create('select', 'playerlocations-players', container);
      let checkOfflineLabel = L.DomUtil.create('label', '', container);
      let checkOfflineCB = L.DomUtil.create('input', 'playerlocations-offline', checkOfflineLabel);
      checkOfflineCB.type = 'checkbox';
      let checkOfflineSpan = L.DomUtil.create('span', '', checkOfflineLabel);
      checkOfflineSpan.innerText = 'Show Offline Players?';
      return addEvents(container, checkOfflineSelect, checkOfflineCB);
    },
    onRemove: function() {

    }
  });

  const addEvents = (container, select, checkbox) => {
    container.addEventListener('playerlocations.update', function(e) {
      select.innerHTML = '<option value="">Follow Player?</option>';
      for ( let n in e.detail ) {
        let o = document.createElement('option');
        o.value = n;
        o.innerText = n;
        o.setAttribute('class', e.detail[n].status === 5 ? 'offline' : 'online');
        if( n === currentSettings.followPlayer ) {
          o.selected = true;
        }
        select.appendChild(o);
      }
    });
    select.addEventListener('change', function(e) {
      let val = this.options[this.selectedIndex].value;
      currentSettings.followPlayer = val === '' ? null : val;
    });
    checkbox.addEventListener('change', function(e) {
      currentSettings.showOfflinePlayers = this.checked;
    });
    return container;
  }

  const updateMarkers = () => {
    const httpRequest = new XMLHttpRequest();
    httpRequest.onload = function(e) {
      let xhr = e.target;
      let data = JSON.parse(xhr.responseText);
      let ps = {};
      for(let i in data.players) {
        let item = data.players[i];
        item.name = i;
        if( item.world === overviewer.current_world && ( item.status === 4 || ( item.status === 5 && currentSettings.showOfflinePlayers) ) ) {
          ps[i] = item;
        }
      }

      let ev = new CustomEvent('playerlocations.update', { detail: ps });
      container.dispatchEvent(ev);
      renderMarkers( ps );
      setTimeout(updateMarkers, currentSettings.refreshRate);
    };
    httpRequest.open('GET', currentSettings.markersFile);
    httpRequest.send(null);
  };

  const renderMarkers = ps => {
    let nicknames = Object.keys(ps);

    markersGroup.eachLayer( m => {
      if( nicknames.indexOf(m.properties.nick) > -1 ) {
        let p = ps[m.properties.nick];
        let position = overviewer.util.fromWorldToLatLng(p.x, p.y, p.z, overviewer.current_layer[overviewer.current_world].tileSetConfig);
        m.setLatLng(position);
        nicknames.splice( nicknames.indexOf(m.properties.nick), 1 );
      }
      else {
        markersGroup.removeLayer(m);
      }
    });

    nicknames.forEach(n => {
      let p = ps[n];
      let position = overviewer.util.fromWorldToLatLng(p.x, p.y, p.z, overviewer.current_layer[overviewer.current_world].tileSetConfig);
      let m = L.marker(position, {
        icon: L.icon({
          iconUrl:getIconUrl(p),
          iconAnchor: [12,12],
          iconSize:   [24,24]
        }),
        title: n
      }).addTo(markersGroup);
      m.properties = {nick: n};
      m.bindPopup(getPopup(p));
    });

    if( currentSettings.followPlayer !== null && ps[currentSettings.followPlayer] ) {
      let p = ps[currentSettings.followPlayer];
      let position = overviewer.util.fromWorldToLatLng(p.x, p.y, p.z, overviewer.current_layer[overviewer.current_world].tileSetConfig);
      overviewer.map.panTo(position);
    }
  };

  const cachedUUIDs = {};

  const getIconUrl = (item, size) => {
    if( currentSettings.skinUrl === '' ) {
      return 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGQAAABkCAIAAAD/gAIDAAAACXBIWXMAAA7EAAAOxAGVKw4bAAACO0lEQVR4nO3cPWtTcRiG8f/JOWmq4stkHATdHap76Si+jLo7FHTwI4iuSkuXLgVxcmwRBx06deisg6tCfaGoHUqgREvak6SDH+B5LnC8fvPNE7hylkMg1dzVsyXSTsJJKaU0VSfcjMZt5lTbjsNNpxN/XCml29ThZjrNXCqpz9M/xgKMBRgLMBZgLMBYgLEAYwHGAowFNMn3vox2Gt86MzuTOXV+pgo34xK/9JVS9oej+NQ0fhUtPlmIsQBjAcYCjAUYCzAWYCzAWICxAGMBxgKazGj58YPMrNeNX5JPzZ7LnDr8cxCP6tQ3Pfj9M9y8eLuZOeWTBRgLMBZgLMBYgLEAYwHGAowFGAswFlCtP30Yjk73eplbf0fxz5l1nTo1OBiEmyv9i5lT3799zswyfLIAYwHGAowFGAswFmAswFiAsQBjAcYCjAU0mZfku09WM7fuLSyFm8WFncypzEvy2vt+5tSb7ZVws/HsUeaUTxZgLMBYgLEAYwHGAowFGAswFmAswFhA9WrxZjjqX7qcudX0LoSbd1tbmVMZ92/NZ2a7P76Em529/cwpnyzAWICxAGMBxgKMBRgLMBZgLMBYgLEAYwHN8Dj+N6Sj3dQvoxvbH8LN7Ws3Mqcynr98nZndmb8ebqrSzZzyyQKMBRgLMBZgLMBYgLEAYwHGAowFGAtovv7aC0eTyX/7T+HNTx8zs7qb+vuljOFh/PI7Oj7KnPLJAowFGAswFmAswFiAsQBjAcYCjAUYCzAWcAKjslawKO+OawAAAABJRU5ErkJggg==';
    }
    if (size == undefined || typeof(size) == undefined || size < 16) {
      size = 24;
    }

    return currentSettings.skinUrl.replace('{u}', item.name).replace('{s}', size);
  }

  const getPopup = function(player) {
    let playerOnline = (player.status == 4 ? "" : (player.status == 5 ? "<i><small>offline</small></i>" : ""))
    return `<div class="infoWindow" style='width: 300px'>
              <table>
                <tr>
                  <td>
                    <img src="${getIconUrl(player, 72)}" style="height: 72px; width: 72px" />
                  </td>
                  <td>
                    <h2>${player.name} ${playerOnline}</h2>
                    <p style="text-align: left;">
                      X: ${player.x}
                      Y: ${player.y}
                      Z: ${player.z}
                    </p>
                  </td>
                </tr>
              </table>
            </div>`;
  };

  init();
};

window.playerMarkers = new PlayerLocations(pmOptions);
