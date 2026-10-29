local function emit(t) localised_print('STATION '..game.table_to_json(t)) end
local function entity(e)
  if not e or not e.valid then return nil end
  return {name=e.name, position=e.position, direction=e.direction, unit_number=e.unit_number}
end
local function stock(e)
  if not e or not e.valid then return nil end
  return {name=e.name, position=e.position, orientation=e.orientation, unit_number=e.unit_number}
end
script.on_init(function()
  local s=game.create_surface('station-oracle', {seed=12345, water='none', starting_area='none', autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
  for y=-864,128,32 do s.request_to_generate_chunks({0,y},0) end
  s.force_generate_chunk_requests()
  local n=0
  for y=-800,100,2 do if s.create_entity{name='straight-rail',position={0,y},direction=defines.direction.north,force='player'} then n=n+1 end end
  local l=s.create_entity{name='locomotive',position={0,0},direction=defines.direction.north,force='player'}
  local a=s.create_entity{name='train-stop',position={2,-300},direction=defines.direction.north,force='player'}
  local b=s.create_entity{name='train-stop',position={2,-700},direction=defines.direction.north,force='player'}
  if not l or not a or not b then emit({kind='setup_error',loco=l~=nil,a=a~=nil,b=b~=nil,rails=n}); return end
  a.backer_name='Alpha'; b.backer_name='Beta'
  local fuel=l.get_fuel_inventory().insert{name='coal',count=30}
  global.loco=l; global.a=a; global.b=b
  emit({kind='setup',rails=n,fuel=fuel,loco=stock(l),a=entity(a),a_rail=entity(a.connected_rail),a_rail_direction=a.connected_rail_direction,b=entity(b),b_rail=entity(b.connected_rail),b_rail_direction=b.connected_rail_direction})
end)
script.on_event(defines.events.on_tick,function(event)
  local l=global.loco
  if not l or not l.valid then return end
  local t=l.train
  if event.tick==0 then
    t.schedule={current=1,records={{station='Alpha',wait_conditions={{type='time',compare_type='and',ticks=60}}},{station='Beta',wait_conditions={{type='time',compare_type='and',ticks=60}}}}}
    t.manual_mode=false
  end
  local p=t.path
  local path=nil
  if p and p.valid then
    path={size=p.size,current=p.current,total_distance=p.total_distance,travelled_distance=p.travelled_distance,is_front=p.is_front}
    if p.current>0 and p.current<=p.size then path.current_rail=entity(p.rails[p.current]) end
  end
  local schedule=t.schedule
  emit({kind='trace',tick=event.tick,speed=t.speed,state=t.state,has_path=t.has_path,position=l.position,orientation=l.orientation,front_stock=stock(t.front_stock),back_stock=stock(t.back_stock),front_rail=entity(t.front_rail),back_rail=entity(t.back_rail),station=entity(t.station),target=entity(t.path_end_stop),target_rail=entity(t.path_end_rail),schedule_current=schedule and schedule.current,path=path})
end)
