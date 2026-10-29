local function emit(t) localised_print('PHYSICS '..game.table_to_json(t)) end
script.on_init(function()
  local s=game.create_surface('oracle', {seed=12345, water='none', starting_area='none', autoplace_controls={['enemy-base']={frequency='none',size='none',richness='none'}}})
  for y=-1664,128,32 do s.request_to_generate_chunks({0,y},0) end
  s.force_generate_chunk_requests()
  local count=0
  for y=-1600,100,2 do
    local r=s.create_entity{name='straight-rail',position={0,y},direction=defines.direction.north,force='player'}
    if r then count=count+1 end
  end
  local loco=s.create_entity{name='locomotive',position={0,0},direction=defines.direction.north,force='player'}
  if not loco then emit({kind='setup_error',what='locomotive',rails=count}); return end
  local stations={}
  for _,x in ipairs({-2,2}) do for _,d in ipairs({defines.direction.north,defines.direction.south}) do
    local y=-1500+#stations*10
    local e=s.create_entity{name='train-stop',position={x,y},direction=d,force='player'}
    if e then e.backer_name='oracle'..(#stations+1); stations[#stations+1]=e end
  end end
  local fuel=loco.get_fuel_inventory().insert{name='coal',count=30}
  local p=loco.prototype
  global.loco=loco; global.stations=stations; global.phase='probe'; global.candidate=1
  emit({kind='setup',rails=count,fuel=fuel,stations=#stations,weight=p.weight,braking_force=p.braking_force,friction_force=p.friction_force,air_resistance=p.air_resistance,max_speed=p.speed})
end)
script.on_event(defines.events.on_tick,function(event)
  local l=global.loco
  if not l or not l.valid then return end
  local t=l.train
  if not t or not t.valid then emit({kind='setup_error',what='train'}); return end
  if global.phase=='probe' then
    local st=global.stations[global.candidate]
    if not st then emit({kind='setup_error',what='no_path'}); global.phase='done'; return end
    if not global.probe_started then
      t.manual_mode=true
      t.schedule={current=1,records={{station=st.backer_name,wait_conditions={{type='time',compare_type='and',ticks=60}}}}}
      t.manual_mode=false
      global.probe_started=event.tick
      emit({kind='candidate',tick=event.tick,name=st.backer_name,x=st.position.x,y=st.position.y,direction=st.direction,connected=st.connected_rail~=nil})
      return
    end
    local b=l.burner
    emit({kind='probe_trace',tick=event.tick,candidate=global.candidate,speed=t.speed,state=t.state,has_path=t.has_path,front_rail=t.front_rail~=nil,back_rail=t.back_rail~=nil,burning=b.currently_burning and b.currently_burning.name or 'none',fuel=b.remaining_burning_fuel,heat=b.heat,energy=l.energy,coal=l.get_fuel_inventory().get_item_count('coal')})
    if event.tick<=global.probe_started+2 then return end
    if t.has_path and t.state==defines.train_state.on_the_path and t.front_rail and t.back_rail then
      global.phase='run'; global.start_tick=event.tick
      emit({kind='valid',tick=event.tick,train_valid=t.valid,has_path=t.has_path,state=t.state,front_rail=t.front_rail~=nil,back_rail=t.back_rail~=nil,coal=l.get_fuel_inventory().get_item_count('coal'),speed=t.speed,weight=t.weight,bonus=l.force.train_braking_force_bonus})
    else
      emit({kind='candidate_failed',tick=event.tick,has_path=t.has_path,state=t.state})
      t.manual_mode=true; global.candidate=global.candidate+1; global.probe_started=nil; return
    end
  end
  if global.phase=='run' or global.phase=='brake' then
    local n=event.tick-global.start_tick
    if n==900 then t.manual_mode=true; global.phase='brake'; emit({kind='brake_command',tick=event.tick,speed=t.speed,state=t.state}) end
    local b=l.burner
    if n<12 or n%20==0 or (n>=815 and n<=830) or global.phase=='brake' then
      emit({kind='trace',tick=event.tick,step=n,phase=global.phase,speed=t.speed,x=l.position.x,y=l.position.y,state=t.state,has_path=t.has_path,front_rail=t.front_rail~=nil,back_rail=t.back_rail~=nil,burning=b.currently_burning and b.currently_burning.name or 'none',fuel=b.remaining_burning_fuel,heat=b.heat,energy=l.energy,coal=l.get_fuel_inventory().get_item_count('coal')})
    end
    if global.phase=='brake' and t.speed==0 then emit({kind='stopped',tick=event.tick,step=n}); global.phase='done' end
  end
end)
